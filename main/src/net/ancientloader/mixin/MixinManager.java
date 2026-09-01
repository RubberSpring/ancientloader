package net.ancientloader.mixin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Small, self-contained mixin transformer for AncientLoader.
 *
 * <p>Mixins are found in every mod jar before its entrypoint runs. They can also
 * be registered explicitly from an entrypoint with {@link #registerMixin(Class)}.
 * Target classes must not have been loaded yet; bytecode cannot safely be
 * replaced after the JVM has defined a class.</p>
 */
public final class MixinManager {
    private static final List<Class<?>> MIXINS = new ArrayList<Class<?>>();
    private static final Set<Class<?>> APPLIED = new HashSet<Class<?>>();

    private MixinManager() {
    }

    public static synchronized void registerMixin(Class<?> mixin) {
        if (mixin.getAnnotation(Mixin.class) == null) {
            throw new IllegalArgumentException(mixin.getName() + " is not annotated with @Mixin");
        }
        if (!MIXINS.contains(mixin)) {
            MIXINS.add(mixin);
        }
    }

    /** Finds classes annotated with {@link Mixin} without initializing them. */
    public static synchronized void discover(ClassLoader loader, File jar) throws IOException {
        JarFile archive = new JarFile(jar);
        try {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().indexOf('$') >= 0) {
                    continue;
                }
                String name = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                try {
                    Class<?> candidate = Class.forName(name, false, loader);
                    if (candidate.getAnnotation(Mixin.class) != null) {
                        registerMixin(candidate);
                    }
                } catch (LinkageError ignored) {
                    // A normal mod class can depend on an optional library. It is not a mixin.
                } catch (ClassNotFoundException ignored) {
                }
            }
        } finally {
            archive.close();
        }
    }

    /** Applies every registered mixin using the active LaunchWrapper class loader. */
    public static synchronized void applyPending() {
        for (Class<?> mixin : MIXINS) {
            if (!APPLIED.contains(mixin)) {
                apply(mixin);
                APPLIED.add(mixin);
            }
        }
    }

    private static void apply(Class<?> mixin) {
        Mixin definition = mixin.getAnnotation(Mixin.class);
        List<String> targets = new ArrayList<String>();
        for (Class<?> target : definition.value()) targets.add(target.getName());
        for (String target : definition.targets()) targets.add(target.replace('/', '.'));
        if (targets.isEmpty()) throw new IllegalArgumentException("@Mixin has no target: " + mixin.getName());

        Object loader = Thread.currentThread().getContextClassLoader();
        if (!hasLaunchWrapperApi(loader)) loader = MixinManager.class.getClassLoader();
        if (!hasLaunchWrapperApi(loader)) {
            throw new IllegalStateException("AncientLoader mixins require the LaunchWrapper class loader");
        }
        for (String target : targets) transform(loader, target, mixin);
    }

    private static boolean hasLaunchWrapperApi(Object loader) {
        try {
            loader.getClass().getMethod("getClass", String.class);
            loader.getClass().getMethod("overrideClass", ClassNode.class);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void transform(Object loader, String targetName, Class<?> mixin) {
        try {
            Method loaded = loader.getClass().getMethod("getLoadedClass", String.class);
            if (loaded.invoke(loader, targetName) != null) {
                throw new IllegalStateException("Cannot mix into already-loaded class " + targetName);
            }
            Method getClass = loader.getClass().getMethod("getClass", String.class);
            ClassNode target = (ClassNode) getClass.invoke(loader, targetName);
            if (target == null) throw new IllegalArgumentException("Mixin target was not found: " + targetName);
            merge(target, readMixin(mixin), mixin.getName().replace('.', '/'));
            loader.getClass().getMethod("overrideClass", ClassNode.class).invoke(loader, target);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not apply " + mixin.getName() + " to " + targetName, e);
        }
    }

    private static ClassNode readMixin(Class<?> mixin) throws IOException {
        String resource = '/' + mixin.getName().replace('.', '/') + ".class";
        ClassReader reader = new ClassReader(mixin.getResourceAsStream(resource));
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private static void merge(ClassNode target, ClassNode mixin, String mixinName) {
        SimpleRemapper remapper = new SimpleRemapper(mixinName, target.name);
        Map<String, MethodNode> copiedHandlers = new HashMap<String, MethodNode>();
        for (FieldNode field : mixin.fields) {
            if (!has(field, Shadow.class) && has(field, Unique.class)) {
                if (findField(target, field.name, field.desc) != null) {
                    throw new IllegalStateException("@Unique field already exists: " + field.name);
                }
                target.fields.add(field);
            }
        }
        for (MethodNode method : mixin.methods) {
            if (method.name.equals("<init>") || method.name.equals("<clinit>")) continue;
            if (has(method, Overwrite.class)) {
                MethodNode original = findMethod(target, method.name, method.desc);
                if (original == null) throw new IllegalStateException("@Overwrite target not found: " + method.name + method.desc);
                target.methods.remove(original);
                target.methods.add(copy(method, remapper, method.name));
            } else if (has(method, Unique.class)) {
                if (findMethod(target, method.name, method.desc) != null) {
                    throw new IllegalStateException("@Unique method already exists: " + method.name + method.desc);
                }
                target.methods.add(copy(method, remapper, method.name));
            }
        }
        for (MethodNode handler : mixin.methods) {
            Inject inject = annotation(handler, Inject.class);
            if (inject == null) continue;
            String name = "ancientloader$" + handler.name + "$" + copiedHandlers.size();
            MethodNode copy = copy(handler, remapper, name);
            target.methods.add(copy);
            copiedHandlers.put(handler.name + handler.desc, copy);
            int matches = 0;
            for (String selector : inject.method()) {
                for (MethodNode method : new ArrayList<MethodNode>(target.methods)) {
                    if (method == copy || !matches(method, selector)) continue;
                    insertInject(target, method, copy, inject);
                    matches++;
                }
            }
            if (matches < inject.require()) {
                throw new IllegalStateException("@Inject expected " + inject.require() + " target(s), found " + matches + ": " + handler.name);
            }
        }
        for (MethodNode handler : mixin.methods) {
            Redirect redirect = annotation(handler, Redirect.class);
            if (redirect == null) continue;
            String name = "ancientloader$" + handler.name + "$redirect";
            MethodNode copy = copy(handler, remapper, name);
            target.methods.add(copy);
            int matches = 0;
            for (String selector : redirect.method()) {
                for (MethodNode method : new ArrayList<MethodNode>(target.methods)) {
                    if (method == copy || !matches(method, selector)) continue;
                    matches += redirectInvocations(target, method, copy, redirect.at());
                }
            }
            if (matches == 0) throw new IllegalStateException("@Redirect found no matching invocation: " + handler.name);
        }
    }

    private static MethodNode copy(MethodNode source, SimpleRemapper remapper, String name) {
        MethodNode destination = new MethodNode(Opcodes.ASM9, source.access, name, source.desc, source.signature,
                source.exceptions == null ? null : source.exceptions.toArray(new String[source.exceptions.size()]));
        source.accept(new MethodRemapper(destination, remapper));
        destination.visibleAnnotations = null;
        destination.invisibleAnnotations = null;
        return destination;
    }

    private static void insertInject(ClassNode owner, MethodNode target, MethodNode handler, Inject inject) {
        String point = inject.at().value().toUpperCase();
        if (!"HEAD".equals(point) && !"RETURN".equals(point) && !"TAIL".equals(point)) {
            throw new UnsupportedOperationException("Only @At(\"HEAD\"), @At(\"RETURN\") and @At(\"TAIL\") are supported");
        }
        Type callbackType = Type.getArgumentTypes(handler.desc)[Type.getArgumentTypes(handler.desc).length - 1];
        boolean returnable = callbackType.getInternalName().equals("net/ancientloader/mixin/CallbackInfoReturnable");
        if (!handler.desc.endsWith(")V") || (!callbackType.getInternalName().equals("net/ancientloader/mixin/CallbackInfo") && !returnable))
            throw new IllegalStateException("@Inject handler must end with CallbackInfo or CallbackInfoReturnable and return void: " + handler.name);
        boolean staticHandler = (handler.access & Opcodes.ACC_STATIC) != 0;
        if ((target.access & Opcodes.ACC_STATIC) != 0 && !staticHandler)
                throw new IllegalStateException("Instance @Inject handler cannot target a static method");
        Type[] args = Type.getArgumentTypes(target.desc);
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        int expected = args.length + 1;
        if (handlerArgs.length != expected) throw new IllegalStateException("@Inject handler arguments must be target arguments followed by CallbackInfo");
        for (int i = 0; i < args.length; i++) if (!args[i].equals(handlerArgs[i]))
            throw new IllegalStateException("@Inject argument " + i + " does not match " + target.name + target.desc);

        if ("HEAD".equals(point)) {
            target.instructions.insert(headCall(owner.name, target, handler, staticHandler, inject.cancellable(), returnable));
            return;
        }
        for (AbstractInsnNode node = target.instructions.getFirst(); node != null; ) {
            AbstractInsnNode next = node.getNext();
            if (isReturn(node.getOpcode())) target.instructions.insertBefore(node, headCall(owner.name, target, handler, staticHandler, false, returnable));
            node = next;
        }
    }

    private static InsnList headCall(String owner, MethodNode target, MethodNode handler, boolean staticHandler, boolean cancellable, boolean returnable) {
        InsnList instructions = new InsnList();
        int ci = nextLocal(target);
        String callback = returnable ? "net/ancientloader/mixin/CallbackInfoReturnable" : "net/ancientloader/mixin/CallbackInfo";
        instructions.add(new TypeInsnNode(Opcodes.NEW, callback));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, callback, "<init>", "()V", false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, ci));
        if (!staticHandler) instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int local = (target.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : Type.getArgumentTypes(target.desc)) {
            instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
        }
        instructions.add(new VarInsnNode(Opcodes.ALOAD, ci));
        instructions.add(new MethodInsnNode(staticHandler ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL, owner, handler.name, handler.desc, false));
        if (cancellable) {
            LabelNode proceed = new LabelNode();
            instructions.add(new VarInsnNode(Opcodes.ALOAD, ci));
            instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/ancientloader/mixin/CallbackInfo", "isCancelled", "()Z", false));
            instructions.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
            if (returnable && Type.getReturnType(target.desc).getSort() != Type.VOID) addReturnValue(instructions, Type.getReturnType(target.desc), ci);
            else addDefaultReturn(instructions, Type.getReturnType(target.desc));
            instructions.add(proceed);
        }
        return instructions;
    }

    private static void addReturnValue(InsnList list, Type type, int callbackLocal) {
        list.add(new VarInsnNode(Opcodes.ALOAD, callbackLocal));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/ancientloader/mixin/CallbackInfoReturnable", "getReturnValue", "()Ljava/lang/Object;", false));
        switch (type.getSort()) {
            case Type.BOOLEAN: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)); list.add(new InsnNode(Opcodes.IRETURN)); break;
            case Type.BYTE: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false)); list.add(new InsnNode(Opcodes.IRETURN)); break;
            case Type.CHAR: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false)); list.add(new InsnNode(Opcodes.IRETURN)); break;
            case Type.SHORT: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false)); list.add(new InsnNode(Opcodes.IRETURN)); break;
            case Type.INT: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)); list.add(new InsnNode(Opcodes.IRETURN)); break;
            case Type.LONG: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false)); list.add(new InsnNode(Opcodes.LRETURN)); break;
            case Type.FLOAT: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false)); list.add(new InsnNode(Opcodes.FRETURN)); break;
            case Type.DOUBLE: list.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double")); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false)); list.add(new InsnNode(Opcodes.DRETURN)); break;
            default: list.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName())); list.add(new InsnNode(Opcodes.ARETURN));
        }
    }

    private static int redirectInvocations(ClassNode owner, MethodNode target, MethodNode handler, At at) {
        if (!"INVOKE".equalsIgnoreCase(at.value()) || at.target().length() == 0)
            throw new UnsupportedOperationException("@Redirect requires @At(value = \"INVOKE\", target = \"owner/name(desc)\")");
        int count = 0;
        for (AbstractInsnNode node = target.instructions.getFirst(); node != null; ) {
            AbstractInsnNode next = node.getNext();
            if (node instanceof MethodInsnNode && matchesInvocation((MethodInsnNode) node, at.target())) {
                if (at.ordinal() >= 0 && count != at.ordinal()) { count++; node = next; continue; }
                replaceInvocation(owner.name, target, handler, (MethodInsnNode) node);
                count++;
                if (at.ordinal() >= 0) return 1;
            }
            node = next;
        }
        return at.ordinal() >= 0 ? 0 : count;
    }

    private static boolean matchesInvocation(MethodInsnNode invocation, String selector) {
        String compact = invocation.owner + "/" + invocation.name + invocation.desc;
        if (compact.equals(selector)) return true;
        String fabric = "L" + invocation.owner + ";" + invocation.name + invocation.desc;
        return fabric.equals(selector);
    }

    private static void replaceInvocation(String owner, MethodNode target, MethodNode handler, MethodInsnNode call) {
        Type[] callArgs = Type.getArgumentTypes(call.desc);
        boolean callStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        int expected = callArgs.length + (callStatic ? 0 : 1);
        if (handlerArgs.length != expected || !Type.getReturnType(handler.desc).equals(Type.getReturnType(call.desc)))
            throw new IllegalStateException("@Redirect handler signature does not match " + call.owner + "." + call.name + call.desc);
        int argument = 0;
        if (!callStatic && !handlerArgs[argument++].equals(Type.getObjectType(call.owner)))
            throw new IllegalStateException("First @Redirect argument must be " + call.owner.replace('/', '.'));
        for (Type type : callArgs) if (!handlerArgs[argument++].equals(type))
            throw new IllegalStateException("@Redirect arguments do not match " + call.name + call.desc);
        boolean staticHandler = (handler.access & Opcodes.ACC_STATIC) != 0;
        if (staticHandler) {
            target.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, owner, handler.name, handler.desc, false));
            return;
        }
        if ((target.access & Opcodes.ACC_STATIC) != 0)
            throw new IllegalStateException("Instance @Redirect handler cannot target a static method");
        InsnList replacement = new InsnList();
        int local = nextLocal(target);
        int[] locals = new int[callArgs.length];
        for (int i = callArgs.length - 1; i >= 0; i--) {
            locals[i] = local;
            local += callArgs[i].getSize();
            replacement.add(new VarInsnNode(callArgs[i].getOpcode(Opcodes.ISTORE), locals[i]));
        }
        int receiver = local;
        replacement.add(new VarInsnNode(Opcodes.ASTORE, receiver));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, receiver));
        for (int i = 0; i < callArgs.length; i++) replacement.add(new VarInsnNode(callArgs[i].getOpcode(Opcodes.ILOAD), locals[i]));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner, handler.name, handler.desc, false));
        target.instructions.insertBefore(call, replacement);
        target.instructions.remove(call);
    }

    private static int nextLocal(MethodNode method) {
        int local = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : Type.getArgumentTypes(method.desc)) local += argument.getSize();
        return Math.max(local, method.maxLocals);
    }

    private static void addDefaultReturn(InsnList list, Type type) {
        switch (type.getSort()) {
            case Type.VOID: list.add(new InsnNode(Opcodes.RETURN)); break;
            case Type.LONG: list.add(new InsnNode(Opcodes.LCONST_0)); list.add(new InsnNode(Opcodes.LRETURN)); break;
            case Type.FLOAT: list.add(new InsnNode(Opcodes.FCONST_0)); list.add(new InsnNode(Opcodes.FRETURN)); break;
            case Type.DOUBLE: list.add(new InsnNode(Opcodes.DCONST_0)); list.add(new InsnNode(Opcodes.DRETURN)); break;
            case Type.ARRAY: case Type.OBJECT: list.add(new InsnNode(Opcodes.ACONST_NULL)); list.add(new InsnNode(Opcodes.ARETURN)); break;
            default: list.add(new InsnNode(Opcodes.ICONST_0)); list.add(new InsnNode(Opcodes.IRETURN));
        }
    }

    private static boolean isReturn(int opcode) { return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN; }
    private static boolean matches(MethodNode method, String selector) { return selector.indexOf('(') >= 0 ? (method.name + method.desc).equals(selector) : method.name.equals(selector); }
    private static MethodNode findMethod(ClassNode node, String name, String desc) { for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(desc)) return method; return null; }
    private static FieldNode findField(ClassNode node, String name, String desc) { for (FieldNode field : node.fields) if (field.name.equals(name) && field.desc.equals(desc)) return field; return null; }
    private static boolean has(Object node, Class<?> annotation) { return annotation((MethodNode) node, annotation) != null; }
    private static boolean has(FieldNode node, Class<?> annotation) {
        if (node.visibleAnnotations == null) return false;
        String descriptor = Type.getDescriptor(annotation);
        for (org.objectweb.asm.tree.AnnotationNode value : node.visibleAnnotations) {
            if (descriptor.equals(value.desc)) return true;
        }
        return false;
    }
    private static <T> T annotation(MethodNode node, Class<T> type) {
        // ASM keeps annotations as metadata; reflection supplies the authoritative API declaration.
        String descriptor = Type.getDescriptor(type);
        if (node.visibleAnnotations == null) return null;
        for (org.objectweb.asm.tree.AnnotationNode value : node.visibleAnnotations) if (descriptor.equals(value.desc)) return proxy(type, value);
        return null;
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(final Class<T> type, final org.objectweb.asm.tree.AnnotationNode node) {
        final Map<String, Object> values = new HashMap<String, Object>();
        if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) values.put((String) node.values.get(i), node.values.get(i + 1));
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            Object value = values.containsKey(method.getName()) ? values.get(method.getName()) : method.getDefaultValue();
            if (value instanceof org.objectweb.asm.tree.AnnotationNode) {
                return proxy(method.getReturnType(), (org.objectweb.asm.tree.AnnotationNode) value);
            }
            if (value instanceof List && method.getReturnType().isArray()) {
                List<?> list = (List<?>) value;
                Class<?> component = method.getReturnType().getComponentType();
                Object array = java.lang.reflect.Array.newInstance(component, list.size());
                for (int i = 0; i < list.size(); i++) java.lang.reflect.Array.set(array, i, list.get(i));
                return array;
            }
            return value;
        });
    }
}
