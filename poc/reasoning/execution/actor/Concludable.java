package grakn.common.poc.reasoning.execution.actor;

import grakn.common.collection.Either;
import grakn.common.concurrent.actor.Actor;
import grakn.common.poc.reasoning.execution.AnswerRecorder;
import grakn.common.poc.reasoning.execution.framework.Answer;
import grakn.common.poc.reasoning.execution.framework.Request;
import grakn.common.poc.reasoning.execution.framework.Response;
import grakn.common.poc.reasoning.mock.MockTransaction;
import grakn.common.poc.reasoning.execution.framework.ExecutionActor;
import grakn.common.poc.reasoning.execution.Registry;
import grakn.common.poc.reasoning.execution.framework.ResponseProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static grakn.common.collection.Collections.map;
import static grakn.common.collection.Collections.pair;

public class Concludable extends ExecutionActor<Concludable> {
    private static final Logger LOG = LoggerFactory.getLogger(Concludable.class);

    private final Long traversalPattern;
    private final long traversalSize;
    private final List<List<Long>> rules;
    private final Map<Actor<Rule>, List<Long>> ruleActorSources;
    private final Set<RuleTrigger> triggered;
    private Actor<AnswerRecorder> executionRecorder;

    public Concludable(Actor<Concludable> self, Long traversalPattern, List<List<Long>> rules, long traversalSize) {
        super(self, Concludable.class.getSimpleName() + "(pattern: " + traversalPattern + ")");
        this.traversalPattern = traversalPattern;
        this.traversalSize = traversalSize;
        this.rules = rules;
        ruleActorSources = new HashMap<>();
        triggered = new HashSet<>();
    }

    @Override
    public Either<Request, Response> receiveRequest(Request fromUpstream, ResponseProducer responseProducer) {
        return produceMessage(fromUpstream, responseProducer);
    }

    @Override
    public Either<Request, Response> receiveAnswer(Request fromUpstream, Response.Answer fromDownstream,
                                                   ResponseProducer responseProducer) {
        // TODO may combine with partial concept maps from the fromUpstream message

        LOG.debug("{}: hasProduced: {}", name, fromDownstream.answer().conceptMap());
        if (!responseProducer.hasProduced(fromDownstream.answer().conceptMap())) {
            responseProducer.recordProduced(fromDownstream.answer().conceptMap());

            // update partial derivation provided from upstream to carry derivations sideways
            Answer.Derivation derivation = new Answer.Derivation(map(pair(fromDownstream.sourceRequest().receiver(), fromDownstream.answer())));
            Answer answer = new Answer(fromDownstream.answer().conceptMap(), traversalPattern.toString(), derivation, self());

            return Either.second(new Response.Answer(fromUpstream, answer, fromUpstream.unifiers()));
        } else {
            Answer.Derivation derivation = new Answer.Derivation(map(pair(fromDownstream.sourceRequest().receiver(), fromDownstream.answer())));
            Answer deduplicated = new Answer(fromDownstream.answer().conceptMap(), traversalPattern.toString(), derivation, self());
            executionRecorder.tell(actor -> actor.record(deduplicated));

            return produceMessage(fromUpstream, responseProducer);
        }
    }

    @Override
    public Either<Request, Response> receiveExhausted(Request fromUpstream, Response.Exhausted fromDownstream, ResponseProducer responseProducer) {
        responseProducer.removeDownstreamProducer(fromDownstream.sourceRequest());
        return produceMessage(fromUpstream, responseProducer);
    }

    @Override
    protected ResponseProducer createResponseProducer(Request request) {
        Iterator<List<Long>> traversal = (new MockTransaction(traversalSize, traversalPattern, 1)).query(request.partialConceptMap());
        ResponseProducer responseProducer = new ResponseProducer(traversal);

        RuleTrigger trigger = new RuleTrigger(request.partialConceptMap());
        if (!triggered.contains(trigger)) {
            registerDownstreamRules(responseProducer, request.path(), request.partialConceptMap(), request.unifiers());
            triggered.add(trigger);
        }
        return responseProducer;
    }

    @Override
    protected void initialiseDownstreamActors(Registry registry) {
        executionRecorder = registry.executionRecorder();
        for (List<Long> rule : rules) {
            Actor<Rule> ruleActor = registry.registerRule(rule, pattern -> Actor.create(self().eventLoopGroup(), actor -> new Rule(actor, pattern, 1L, 0L)));
            ruleActorSources.put(ruleActor, rule);
        }
    }

    private Either<Request, Response> produceMessage(Request fromUpstream, ResponseProducer responseProducer) {
        while (responseProducer.hasTraversalProducer()) {
            List<Long> conceptMap = responseProducer.traversalProducer().next();
            LOG.debug("{}: hasProduced: {}", name, conceptMap);
            if (!responseProducer.hasProduced(conceptMap)) {
                responseProducer.recordProduced(conceptMap);
                Answer answer = new Answer(conceptMap, traversalPattern.toString(), new Answer.Derivation(map()), self());
                return Either.second(new Response.Answer(fromUpstream, answer, fromUpstream.unifiers()));
            }
        }

        if (responseProducer.hasDownstreamProducer()) {
            return Either.first(responseProducer.nextDownstreamProducer());
        } else {
            return Either.second(new Response.Exhausted(fromUpstream));
        }
    }

    private void registerDownstreamRules(ResponseProducer responseProducer, Request.Path path, List<Long> partialConceptMap,
                                         List<Object> unifiers) {
        for (Actor<Rule> ruleActor : ruleActorSources.keySet()) {
            Request toDownstream = new Request(path.append(ruleActor), partialConceptMap, unifiers, Answer.Derivation.EMPTY);
            responseProducer.addDownstreamProducer(toDownstream);
        }
    }

    @Override
    protected void exception(Exception e) {
        LOG.error("Actor exception", e);
        // TODO, once integrated into the larger flow of executing queries, kill the actors and report and exception to root
    }

    private static class RuleTrigger {
        private final List<Long> partialConceptMap;

        public RuleTrigger(List<Long> partialConceptMap) {
            this.partialConceptMap = partialConceptMap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RuleTrigger that = (RuleTrigger) o;
            return Objects.equals(partialConceptMap, that.partialConceptMap);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partialConceptMap);
        }
    }

}

