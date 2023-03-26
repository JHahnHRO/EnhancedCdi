package io.github.jhahnhro.enhancedcdi.util;

import static java.util.function.Predicate.not;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Iteration {

    private Iteration() {}

    public static <X> Set<X> breadthFirstSearch(X startVertex, Function<? super X, Stream<X>> edges) {
        return breadthFirstSearch(startVertex, edges, x -> {});
    }

    public static <X> Set<X> breadthFirstSearch(X startVertex, Function<? super X, Stream<X>> edges,
                                                final Consumer<X> action) {
        final BreadthFirstIterator<X> iterator = new BreadthFirstIterator<>(startVertex, edges);
        iterator.forEachRemaining(action);
        return iterator.getAlreadyVisited();
    }

    public static <X> Set<X> depthFirstSearch(X startVertex, Function<? super X, Stream<X>> edges) {
        return depthFirstSearch(startVertex, edges, x -> {});
    }

    public static <X> Set<X> depthFirstSearch(X startVertex, Function<? super X, Stream<X>> edges, Consumer<X> action) {
        final DepthFirstIterator<X> iterator = new DepthFirstIterator<>(startVertex, edges);
        iterator.forEachRemaining(action);
        return iterator.getAlreadyVisited();
    }

    public static class BreadthFirstIterator<X> implements Iterator<X> {
        private final Set<X> alreadyVisited;
        private final Queue<X> notYetVisited;
        private final Function<? super X, Stream<X>> edges;

        public BreadthFirstIterator(X startVertex, Function<? super X, Stream<X>> edges) {
            this.alreadyVisited = new LinkedHashSet<>();
            this.notYetVisited = new LinkedList<>();
            this.notYetVisited.add(Objects.requireNonNull(startVertex));
            this.edges = edges.andThen(stream -> stream.filter(not(alreadyVisited::contains)));
        }

        @Override
        public boolean hasNext() {
            return !notYetVisited.isEmpty();
        }

        @Override
        public X next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final X next = this.notYetVisited.remove();
            alreadyVisited.add(next);

            final Stream<X> neighbours = edges.apply(next);
            neighbours.forEach(notYetVisited::add);

            return next;
        }

        /**
         * @return an unmodifiable view of the Set of the graph's vertices that have already been visited by this
         * iterator.
         */
        public Set<X> getAlreadyVisited() {
            return Collections.unmodifiableSet(alreadyVisited);
        }

    }

    /**
     * An {@link Iterator} that traverses a graph (defined implicitly by the {@code edges} constructor argument) in a
     * depth-first ordering. If the {@link Stream} returned by {@code edges} has a reliable encounter order (for example
     * because it comes from {@link List#stream()} or {@link LinkedHashSet#stream()} or something similar), then this
     * iterator will do the "pre-ordering" variant of depth-first transversal.
     *
     * @param <X> the type of vertices of the graph
     */
    public static class DepthFirstIterator<X> implements Iterator<X> {
        private final Set<X> alreadyVisited;
        // our stack
        private final List<X> notYetVisited;
        private final Function<? super X, Stream<X>> edges;

        public DepthFirstIterator(X startVertex, Function<? super X, Stream<X>> edges) {
            this.alreadyVisited = new LinkedHashSet<>();
            this.notYetVisited = new LinkedList<>();
            this.notYetVisited.add(Objects.requireNonNull(startVertex));
            this.edges = edges.andThen(stream -> stream.filter(not(alreadyVisited::contains)));
        }

        @Override
        public boolean hasNext() {
            return !notYetVisited.isEmpty();
        }

        @Override
        public X next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final X next = notYetVisited.remove(0); // stack.pop()
            alreadyVisited.add(next);

            final Stream<X> neighbours = edges.apply(next);
            // push all neighbours to the stack all at once so that they are popped off the stack in the same order
            // as they occur in the stream. This ensures the pre-ordering traversal.
            notYetVisited.addAll(0, neighbours.toList());

            return next;
        }


        /**
         * @return an unmodifiable view of the Set of the graph's vertices that have already been visited by this
         * iterator.
         */
        public Set<X> getAlreadyVisited() {
            return Collections.unmodifiableSet(alreadyVisited);
        }
    }
}
