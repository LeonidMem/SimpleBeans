package ru.leonidm.simplebeans.beans;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.applications.ApplicationContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BeansDependencyTree {

    private static final BeanData CONTEXT_BEAN_DATA = new BeanData(ApplicationContext.class, "");
    private static final Node CONTEXT_NODE = new Node(CONTEXT_BEAN_DATA);
    private final Set<Node> headNodes = new HashSet<>();
    private final Map<BeanData, Node> beanClassToNode = new HashMap<>();
    private final ApplicationContext context;
    private boolean initialized = false;

    public BeansDependencyTree(@NotNull ApplicationContext context) {
        this.context = context;

        beanClassToNode.put(CONTEXT_BEAN_DATA, CONTEXT_NODE);
    }

    public void add(@NotNull BeanInitializer<?> beanInitializer) {
        Class<?> beanClass = beanInitializer.getBeanClass();
        String id = beanInitializer.getId();

        BeanData beanData = new BeanData(beanClass, id);

        Node node = beanClassToNode.computeIfAbsent(beanData, k -> new Node(beanInitializer));
        if (node.beanInitializer == null) {
            node.beanInitializer = beanInitializer;
        }

        for (BeanData dependency : beanInitializer.getDependencies()) {
            if (dependency.equals(beanData)) {
                throw new IllegalStateException("Cycle dependency: %s depends on itself".formatted(node.beanData));
            }

            Node dependencyNode = beanClassToNode.computeIfAbsent(dependency, k -> new Node(dependency));
            if (doesDependOn(dependencyNode, node)) {
                throw new IllegalStateException("Cycle dependency: %s and %s depend on each other"
                        .formatted(node.beanData, dependencyNode.beanData));
            }

            dependencyNode.children.add(node);
            node.parents.add(dependencyNode);
        }

        if (node.parents.size() == 0) {
            headNodes.add(node);
        }
    }

    private boolean doesDependOn(@NotNull Node node, @NotNull Node dependency) {
        for (Node currentNode : node.parents) {
            if (currentNode == dependency || doesDependOn(currentNode, dependency)) {
                return true;
            }
        }

        return false;
    }

    public void initializeBeans() {
        if (initialized) {
            throw new IllegalStateException("Already initialized");
        }

        initialized = true;

        validateBeans();

        Set<Node> nodes = headNodes;
        while (!nodes.isEmpty()) {
            Set<Node> nextNodes = new HashSet<>();

            for (Node node : nodes) {
                context.addBean((Class) node.beanData.getBeanClass(), node.beanData.getId(), node.beanInitializer.create());

                for (Node childrenNode : node.children) {
                    childrenNode.parents.remove(node);

                    if (childrenNode.parents.isEmpty()) {
                        nextNodes.add(childrenNode);
                    }
                }
            }

            nodes = nextNodes;
        }
    }

    private void validateBeans() {
        for (Node node : beanClassToNode.values()) {
            for (Node parentNode : node.parents) {
                Node dependency = beanClassToNode.get(parentNode.beanData);
                if (dependency == null || dependency.beanInitializer == null && !dependency.beanData.equals(CONTEXT_BEAN_DATA)) {
                    throw new IllegalStateException("%s depends on %s that is not reachable"
                            .formatted(node.beanData, parentNode.beanData));
                }
            }
        }
    }

    private static class Node {

        /**
         * Children are nodes that depends on this node
         */
        private final Set<Node> children = new HashSet<>();
        /**
         * Parents are dependencies of this node
         */
        private final Set<Node> parents = new HashSet<>();
        private final BeanData beanData;
        private BeanInitializer<?> beanInitializer;

        private Node(@NotNull BeanInitializer<?> beanInitializer) {
            this.beanInitializer = beanInitializer;
            this.beanData = BeanData.of(beanInitializer);
        }

        private Node(@NotNull BeanData beanData) {
            this.beanInitializer = null;
            this.beanData = beanData;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "beanData=" + shortenedBeanData(beanData) +
                    ", children=" + children.stream().map(n -> n.beanData).map(this::shortenedBeanData).collect(Collectors.toList()) +
                    ", parents=" + parents.stream().map(n -> n.beanData).map(this::shortenedBeanData).collect(Collectors.toList()) +
                    '}';
        }

        @NotNull
        private String shortenedBeanData(@NotNull BeanData beanData) {
            return beanData.getBeanClass().getSimpleName() + "{id=" + beanData.getId() + "}";
        }
    }
}
