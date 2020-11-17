package grakn.common.poc.reasoning.test;

import grakn.common.concurrent.actor.Actor;
import grakn.common.concurrent.actor.eventloop.EventLoopGroup;
import grakn.common.poc.reasoning.Concludable;
import grakn.common.poc.reasoning.Conjunction;
import grakn.common.poc.reasoning.Registry;
import grakn.common.poc.reasoning.Rule;
import grakn.common.poc.reasoning.execution.Request;
import grakn.common.poc.reasoning.execution.Response;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static grakn.common.collection.Collections.list;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class ReasoningTest {
    @Test
    public void singleAtomicActor() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomicPattern = 0L;
        long atomicTraversalSize = 5L;
        registerAtomic(atomicPattern, list(), atomicTraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomicPattern);
        long conjunctionTraversalSize = 5L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, -10L, responses, elg);

        assertResponsesSync(conjunction, atomicTraversalSize + conjunctionTraversalSize, responses, registry);
    }

    @Test
    public void doubleAtomicActors() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = 2L;
        long atomic1TraversalSize = 2L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        long atomic2Pattern = 20L;
        long atomic2TraversalSize = 2L;
        registerAtomic(atomic2Pattern, list(), atomic2TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic2Pattern, atomic1Pattern);
        long conjunctionTraversalSize = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize,0L,  responses, elg);

        assertResponses(conjunction, conjunctionTraversalSize + (atomic2TraversalSize * atomic1TraversalSize), responses, registry);
    }

    @Test
    public void filteringAtomicActor() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = 2L;
        long atomic1TraversalSize = 2L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        long atomic2Pattern = 20L;
        long atomic2TraversalSize = 0L;
        registerAtomic(atomic2Pattern, list(), atomic2TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic2Pattern, atomic1Pattern);
        long conjunctionTraversalSize = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize,0L,  responses, elg);

        assertResponses(conjunction, conjunctionTraversalSize + (atomic1TraversalSize * atomic2TraversalSize), responses, registry);
    }

    @Test
    public void simpleRule() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = -2L;
        long atomic1TraversalSize = 1L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        List<Long> rulePattern = list(-2L);
        long ruleTraversalSize = 0L;
        long ruleTraversalOffset = 0L;
        registerRule(rulePattern, ruleTraversalSize, ruleTraversalOffset, registry, elg);

        long atomic2Pattern = 2L;
        long atomic2TraversalSize = 1L;
        registerAtomic(atomic2Pattern, list(rulePattern), atomic2TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic2Pattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = conjunctionTraversalSize + atomic2TraversalSize + ruleTraversalSize + atomic1TraversalSize;
        assertResponses(conjunction, answerCount, responses, registry);
    }

    @Test
    public void atomicChainWithRule() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = -2L;
        long atomic1TraversalSize = 1L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        List<Long> rulePattern = list(atomic1Pattern);
        long ruleTraversalSize = 1L;
        long ruleTraversalOffset = -10L;
        registerRule(rulePattern, ruleTraversalSize, ruleTraversalOffset, registry, elg);

        long atomic2Pattern = 2L;
        long atomic2TraversalSize = 1L;
        registerAtomic(atomic2Pattern, list(rulePattern), atomic2TraversalSize, registry, elg);

        long atomic3Pattern = 20L;
        long atomic3TraversalSize = 1L;
        registerAtomic(atomic3Pattern, list(), atomic3TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic3Pattern, atomic2Pattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = conjunctionTraversalSize + (atomic3TraversalSize * (atomic2TraversalSize + ruleTraversalSize + atomic1TraversalSize));
        assertResponses(conjunction, answerCount, responses, registry);
    }

    @Test
    public void shallowRerequest() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = 2L;
        long atomic1TraversalSize = 2L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        long atomic2Pattern = 20L;
        long atomic2TraversalSize = 2L;
        registerAtomic(atomic2Pattern, list(), atomic2TraversalSize, registry, elg);

        long atomic3Pattern = 200L;
        long atomic3TraversalSize = 2L;
        registerAtomic(atomic3Pattern, list(), atomic3TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic3Pattern, atomic2Pattern, atomic1Pattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = conjunctionTraversalSize + (atomic3TraversalSize * atomic2TraversalSize * atomic1TraversalSize);
        assertResponses(conjunction, answerCount, responses, registry);
    }

    @Test
    public void deepRerequest() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = 2L;
        long atomic1TraversalSize = 10L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        long atomic2Pattern = 20L;
        long atomic2TraversalSize = 10L;
        registerAtomic(atomic2Pattern, list(), atomic2TraversalSize, registry, elg);

        long atomic3Pattern = 200L;
        long atomic3TraversalSize = 10L;
        registerAtomic(atomic3Pattern, list(), atomic3TraversalSize, registry, elg);

        long atomic4Pattern = 2000L;
        long atomic4TraversalSize = 10L;
        registerAtomic(atomic4Pattern, list(), atomic4TraversalSize, registry, elg);

        long atomic5Pattern = 20000L;
        long atomic5TraversalSize = 10L;
        registerAtomic(atomic5Pattern, list(), atomic5TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic5Pattern, atomic4Pattern, atomic3Pattern, atomic2Pattern, atomic1Pattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = conjunctionTraversalSize + (atomic5TraversalSize * atomic4TraversalSize * atomic3TraversalSize * atomic2TraversalSize * atomic1TraversalSize);
        assertResponses(conjunction, answerCount, responses, registry);
    }

    @Test
    public void bulkActorCreation() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long start = System.currentTimeMillis();

        long atomicPattern = 1L;
        List<List<Long>> atomicRulePatterns = new ArrayList<>();
        for (long i = 2L; i < 1000_000L; i++) {
            List<Long> pattern = list(i);
            atomicRulePatterns.add(pattern);
        }
        long atomicTraversalSize = 1L;
        registerAtomic(atomicPattern, atomicRulePatterns, atomicTraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomicPattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        conjunction.tell(actor ->
                actor.executeReceiveRequest(
                        new Request(new Request.Path(conjunction), list(), list(), list(), null),
                        registry
                )
        );
        responses.take();

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("elapsed = " + elapsed);
    }

    @Test
    public void loopTermination() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomicPattern = 1L;

        List<Long> rulePattern = list(atomicPattern);
        long ruleTraversalSize = 1L;
        long ruleTraversalOffset = 0L;
        registerRule(rulePattern, ruleTraversalSize, ruleTraversalOffset, registry, elg);

        long atomic1TraversalSize = 1L;
        registerAtomic(atomicPattern, list(rulePattern), atomic1TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomicPattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = 0L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = conjunctionTraversalSize + atomic1TraversalSize + ruleTraversalSize + atomic1TraversalSize;
        assertResponses(conjunction, answerCount, responses, registry);
    }

    @Test
    public void deduplication() throws InterruptedException {
        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long traversalSize = 100L;

        long atomicPattern = 1L;
        long atomicTraversalSize = traversalSize;
        registerAtomic(atomicPattern, list(), atomicTraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomicPattern);
        long conjunctionTraversalSize = traversalSize;
        long conjunctionTraversalOffset = 1L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = traversalSize;
        assertResponses(conjunction, answerCount, responses, registry);
    }



    @Test
    public void explanationTest() throws InterruptedException {
        /*

        when {
            $x isa person, has age 10;
        }, then {
            $x has birth-year 2010;
        };

        query:
        match $x has birth-year 2010;

         */

        Registry registry = new Registry();
        LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<>();
        EventLoopGroup elg = new EventLoopGroup(1, "reasoning-elg");

        long atomic1Pattern = 10L;
        long atomic1TraversalSize = 1L;
        registerAtomic(atomic1Pattern, list(), atomic1TraversalSize, registry, elg);

        List<Long> rulePattern = list(10L);
        long ruleTraversalSize = 0L;
        long ruleTraversalOffset = 0L;
        registerRule(rulePattern, ruleTraversalSize, ruleTraversalOffset, registry, elg);

        long atomic2Pattern = 2010L;
        long atomic2TraversalSize = 0L;
        registerAtomic(atomic2Pattern, list(rulePattern), atomic2TraversalSize, registry, elg);

        List<Long> conjunctionPattern = list(atomic2Pattern);
        long conjunctionTraversalSize = 0L;
        long conjunctionTraversalOffset = -10L;
        Actor<Conjunction> conjunction = registerConjunction(conjunctionPattern, conjunctionTraversalSize, conjunctionTraversalOffset, responses, elg);

        long answerCount = conjunctionTraversalSize + atomic2TraversalSize + ruleTraversalSize + atomic1TraversalSize;

        for (int i = 0; i < answerCount; i++) {
            conjunction.tell(actor ->
                    actor.executeReceiveRequest(
                            new Request(new Request.Path(conjunction), list(), list(), list(), null),
                            registry
                    )
            );
            Response answer = responses.take();
        }
    }


    private void registerAtomic(long pattern, List<List<Long>> rules, long traversalSize, Registry registry, EventLoopGroup elg) {
        registry.registerAtomic(pattern, p -> Actor.create(elg, self -> new Concludable(self, p, rules, traversalSize)));
    }

    private Actor<Conjunction> registerConjunction(List<Long> pattern, long traversalSize, long traversalOffset, LinkedBlockingQueue<Response> responses, EventLoopGroup elg) {
        return Actor.create(elg, self -> new Conjunction(self, pattern, traversalSize, traversalOffset, responses));
    }

    private void registerRule(List<Long> pattern, long traversalSize, long traversalOffset, Registry registry, EventLoopGroup elg) {
        registry.registerRule(pattern, p -> Actor.create(elg, self -> new Rule(self, p, traversalSize, traversalOffset)));
    }

    private void assertResponses(Actor<Conjunction> conjunction, long answerCount, LinkedBlockingQueue<Response> responses, Registry registry) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long n = answerCount + 1; //total number of traversal answers, plus one expected Exhausted (-1 answer)
        for (int i = 0; i < n; i++) {
            conjunction.tell(actor ->
                    actor.executeReceiveRequest(
                            new Request(new Request.Path(conjunction), list(), list(), list(), null),
                            registry
                    )
            );
        }

        for (int i = 0; i < n - 1; i++) {
            Response answer = responses.take();
            assertTrue(answer.isAnswer());
        }
        assertTrue(responses.take().isExhausted());
//        Thread.sleep(1000); // enable for debugging to ensure equivalent debug vs normal execution
        System.out.println("Time : " + (System.currentTimeMillis() - startTime));
        assertTrue(responses.isEmpty());
    }

    private void assertResponsesSync(Actor<Conjunction> conjunction, long answerCount, LinkedBlockingQueue<Response> responses, Registry registry) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long n = answerCount + 1; //total number answers, plus one expected DONE (-1 answer)
        for (int i = 0; i < n; i++) {
            conjunction.tell(actor ->
                    actor.executeReceiveRequest(new Request(new Request.Path(conjunction), list(), list(), list(), null), registry));
            Response answer = responses.take();
            System.out.println(answer);
            if (i < n - 1) {
                assertTrue(answer.isAnswer());
            } else {
                assertTrue(answer.isExhausted());
            }
        }
        System.out.println("Time : " + (System.currentTimeMillis() - startTime));
    }
}