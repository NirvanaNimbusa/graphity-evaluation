/**
 * compares 2 nodes acording to a property compeartor
 * 
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation.neo;

import java.util.Comparator;

import org.neo4j.graphdb.Node;

public class NodeComparator<T> implements Comparator<Node> {
	private final String property;
	private final Comparator<T> propertyComparator;

	NodeComparator(String property, Comparator<T> propertyComparator) {
		this.property = property;
		this.propertyComparator = propertyComparator;
	}

	@SuppressWarnings("unchecked")
	public int compare(Node n1, Node n2) {
		if (!n1.hasProperty(property) && !n2.hasProperty(property)) {
			return 1;
		} else {
			if (!n1.hasProperty(property)) {
				return 1;
			}
			if (!n2.hasProperty(property)) {
				return -1;
			}
		}
		return propertyComparator.compare((T) n1.getProperty(property),
				(T) n2.getProperty(property));
	}
}
