/**
 * helps to iterate through the linked list encoding the graphity index
 * 
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation.neo;

import java.util.Iterator;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;

public class NodeQueueIterator implements Iterator<Node> {
	static int next = 0;
	static int hasNext = 0;
	private Node currentNode;
	private final RelationshipType type;
	private final Direction dir;
	private Node nextNode;

	public NodeQueueIterator(Node n, RelationshipType type, Direction dir) {
		currentNode = n;
		this.type = type;
		this.dir = dir;
	}

	@Override
	public boolean hasNext() {
		// H.pln("hasNext: "+hasNext++);
		if (currentNode == null) {
			return false;
		}
		nextNode = NeoUtils.getNextSingleNode(currentNode, type);
		return nextNode != null;
	}

	@Override
	public Node next() {
		// H.pln("next: "+next++);
		if (currentNode == null) {
			return null;
		}
		if (nextNode != null) {
			currentNode = nextNode;
			nextNode = null;
			return currentNode;
		}

		if (dir == Direction.OUTGOING) {
			currentNode = NeoUtils.getNextSingleNode(currentNode, type);
		} else {
			currentNode = NeoUtils.getPrevSingleNode(currentNode, type);
		}
		return currentNode;
	}

	@Override
	public void remove() {
		throw new Error("Not implemented!!!");
	}

	public Node current() {
		return currentNode;
	}
}
