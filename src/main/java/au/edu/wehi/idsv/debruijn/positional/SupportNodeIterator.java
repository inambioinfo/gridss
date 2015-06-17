package au.edu.wehi.idsv.debruijn.positional;

import java.util.Iterator;
import java.util.PriorityQueue;

import au.edu.wehi.idsv.DirectedEvidence;
import au.edu.wehi.idsv.NonReferenceReadPair;
import au.edu.wehi.idsv.SoftClipEvidence;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

/**
 * Transforms a breakend start DirectedEvidence iterator
 * into a start position sorted kmer support iterator
 * @author Daniel Cameron
 *
 */
public class SupportNodeIterator implements Iterator<KmerSupportNode> {
	private final PeekingIterator<DirectedEvidence> underlying;
	private final int k;
	/**
	 * Position to emit kmers as no more can be added
	 *                  | input position
	 *                  v
	 *             ?????------------<====  RP
	 *              MMMMMSSSSS SC
	 *              SSSSSMMMMM SC
	 *              ====>------------?????  RP
	 */
	private final int emitOffset;
	private final PriorityQueue<KmerSupportNode> buffer = new PriorityQueue<KmerSupportNode>(1024, KmerNode.ByStartPosition);
	private int inputPosition = Integer.MIN_VALUE;
	private int firstReferenceIndex;
	private int lastPosition = Integer.MIN_VALUE;
	public SupportNodeIterator(int k, Iterator<DirectedEvidence> it, int maxReadLength) {
		this.underlying = Iterators.peekingIterator(it);
		this.k = k;
		this.emitOffset = maxReadLength + 1;
		if (underlying.hasNext()) {
			firstReferenceIndex = underlying.peek().getBreakendSummary().referenceIndex;
		}
	}
	private void process(DirectedEvidence de) {
		assert(de.getBreakendSummary().referenceIndex == firstReferenceIndex);
		Evidence e;
		if (de instanceof SoftClipEvidence) {
			e = Evidence.create(k, (SoftClipEvidence)de, true);
		} else if (de instanceof NonReferenceReadPair) {
			e = Evidence.create(k, (NonReferenceReadPair)de);
		} else {
			throw new RuntimeException("Assembler able to process only soft clip and read pair evidence");
		}
		for (int i = 0; i < e.length(); i++) {
			KmerSupportNode support = e.node(i); 
			if (support != null) {
				buffer.add(support);
			}
		}
	}
	@Override
	public boolean hasNext() {
		ensureBuffer();
		return !buffer.isEmpty();
	}
	@Override
	public KmerSupportNode next() {
		ensureBuffer();
		KmerSupportNode node = buffer.poll();
		assert(node.startPosition() >= lastPosition);
		lastPosition = node.startPosition();
		return node;
	}
	private void ensureBuffer() {
		while (underlying.hasNext() && (buffer.isEmpty() || buffer.peek().startPosition() > inputPosition - emitOffset)) {
			inputPosition = underlying.peek().getBreakendSummary().start;
			advance();
		}
		if (!underlying.hasNext()) {
			inputPosition = Integer.MAX_VALUE;
		}
	}
	private void advance() {
		while (underlying.hasNext() && underlying.peek().getBreakendSummary().start <= inputPosition) {
			process(underlying.next());
		}
	}
	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}