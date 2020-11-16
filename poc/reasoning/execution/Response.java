package grakn.common.poc.reasoning.execution;

import grakn.common.poc.reasoning.Explanation;

import javax.annotation.Nullable;
import java.util.List;

public interface Response {
    Request sourceRequest();

    boolean isAnswer();
    boolean isExhausted();

    default Response.Answer asAnswer() {
        throw new ClassCastException("Cannot cast " + this.getClass().getSimpleName() + " to " + Response.Answer.class.getSimpleName());
    }

    default Response.Exhausted asExhausted() {
        throw new ClassCastException("Cannot cast " + this.getClass().getSimpleName() + " to " + Response.Exhausted.class.getSimpleName());
    }

    class Answer implements Response {
        private final Request sourceRequest;
        private final List<Long> partialAnswer;
        private final List<Object> constraints;
        private final List<Object> unifiers;

        private final Explanation explanation;

        public Answer(final Request sourceRequest,
                      final List<Long> partialAnswer,
                      final List<Object> constraints,
                      final List<Object> unifiers,
                      @Nullable Explanation explanation) {
            this.sourceRequest = sourceRequest;
            this.partialAnswer = partialAnswer;
            this.constraints = constraints;
            this.unifiers = unifiers;
            this.explanation = explanation;
        }

        @Override
        public Request sourceRequest() {
            return sourceRequest;
        }

        public List<Long> partialAnswer() {
            return partialAnswer;
        }

        public List<Object> constraints() {
            return constraints;
        }

        public List<Object> unifiers() {
            return unifiers;
        }

        @Override
        public boolean isAnswer() { return true; }

        @Override
        public boolean isExhausted() { return false; }

        @Override
        public Response.Answer asAnswer() {
            return this;
        }

    }

    class Exhausted implements Response {
        private final Request sourceRequest;

        public Exhausted(final Request sourceRequest) {
            this.sourceRequest = sourceRequest;
        }

        @Override
        public Request sourceRequest() {
            return sourceRequest;
        }

        @Override
        public boolean isAnswer() { return false; }

        @Override
        public boolean isExhausted() { return true; }

        @Override
        public Response.Exhausted asExhausted() {
            return this;
        }
    }
}