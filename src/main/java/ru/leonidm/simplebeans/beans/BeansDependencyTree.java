package ru.leonidm.simplebeans.beans;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.applications.ApplicationContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BeansDependencyTree {

    private final Set<Node> headNodes = new HashSet<>();
    private final Map<BeanData, Node> beanClassToNode = new HashMap<>();
    private final ApplicationContext context;
    private boolean initialized = false;

    public BeansDependencyTree(@NotNull ApplicationContext context) {
        this.context = context;
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
            if (dependencyNode.parents.contains(node)) {
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
                if (!beanClassToNode.containsKey(parentNode.beanData)) {
                    throw new IllegalStateException("%s depends on %s that is not reachable"
                            .formatted(node.beanData, parentNode.beanData));
                }
            }
        }
    }

    private static class Node {

        private final Set<Node> children = new HashSet<>();
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
                    "children=" + children.stream().map(n -> n.beanData).map(this::temp).collect(Collectors.toList()) +
                    ", parents=" + parents.stream().map(n -> n.beanData).map(this::temp).collect(Collectors.toList()) +
                    ", beanData=" + temp(beanData) +
                    '}';
        }

        // TODO: remove
        @NotNull
        private String temp(@NotNull BeanData beanData) {
            return beanData.getBeanClass().getSimpleName() + "{id=" + beanData.getId() + "}";
        }
    }
}
