package io.github.jhahnhro.enhancedcdi.util;

import static java.util.function.Predicate.not;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class Iteration {

    private Iteration() {}

    public static <X> SequencedSet<X> breadthFirstSearch(X startVertex, Function<? super X, Stream<X>> edges) {
        final BreadthFirstIterator<X> iterator = new BreadthFirstIterator<>(startVertex, edges);
        iterator.forEachRemaining(x -> {});
        return iterator.getAlreadyVisited();
    }

    public static <X> SequencedSet<X> depthFirstSearch(X startVertex, Function<? super X, Stream<X>> edges) {
        final DepthFirstIterator<X> iterator = new DepthFirstIterator<>(startVertex, edges);
        iterator.forEachRemaining(x -> {});
        return iterator.getAlreadyVisited();
    }

    public static class BreadthFirstIterator<X> extends AbstractGraphIterator<X> {

        public BreadthFirstIterator(X startVertex, Function<? super X, Stream<X>> edges) {
            super(startVertex, edges, LinkedList::new); // LinkedList used as queue
        }

        @Override
        protected void updateNextVertices(X currentVertex, Stream<X> neighbours) {
            neighbours.forEach(nextVertices::addLast);
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
    public static class DepthFirstIterator<X> extends AbstractGraphIterator<X> {

        public DepthFirstIterator(X startVertex, Function<? super X, Stream<X>> edges) {
            super(startVertex, edges, LinkedList::new); // LinkedList used as stack
        }

        @Override
        protected void updateNextVertices(X currentVertex, Stream<X> neighbours) {
            // push all neighbours to the stack all at once so that they are popped off the stack in the same order
            // as they occur in the stream. This ensures the pre-ordering traversal.
            final List<X> list = neighbours.toList();
            list.reversed().forEach(nextVertices::addFirst);
        }
    }

    abstract static class AbstractGraphIterator<X> implements Iterator<X> {
        protected final SequencedCollection<X> nextVertices;
        private final SequencedSet<X> alreadyVisited;
        private final Function<? super X, Stream<X>> edges;

        AbstractGraphIterator(X startVertex, Function<? super X, Stream<X>> edges, Supplier<?
                extends SequencedCollection<X>> supplier) {
            this.alreadyVisited = new LinkedHashSet<>();
            this.nextVertices = supplier.get();
            this.nextVertices.add(startVertex);
            this.edges = edges.andThen(stream -> stream.filter(not(alreadyVisited::contains)));
        }

        @Override
        public boolean hasNext() {
            return !nextVertices.isEmpty();
        }

        @Override
        public X next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            final X next = nextVertices.removeFirst();
            alreadyVisited.add(next);

            updateNextVertices(next, edges.apply(next));

            return next;
        }

        protected abstract void updateNextVertices(X currentVertex, Stream<X> neighbours);

        /**
         * @return an unmodifiable view of the Set of the graph's vertices that have already been visited by this
         * iterator.
         */
        public SequencedSet<X> getAlreadyVisited() {
            return Collections.unmodifiableSequencedSet(alreadyVisited);
        }
    }
}
