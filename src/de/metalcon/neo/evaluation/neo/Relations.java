/**
 * some of the used relationships
 *
 * @author Jonas Kunze, Rene Pickhardt
 * 
 */

package de.metalcon.neo.evaluation.neo;

import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.RelationshipType;

public enum Relations implements RelationshipType {
	UPDATE("UPDATE"), FOLLOWS("FOLLOWS"), FRIENDUPDATE("FRIENDUPDATE");

	private final String name;

	private Relations(String name) {
		this.name = name;
	}

	public String toString() {
		return name;
	}

	static DynamicRelationshipType getEgoRelation(long key) {
		return DynamicRelationshipType.withName("ego:" + key);
	}

	static DynamicRelationshipType getCiRelation(long key) {
		return DynamicRelationshipType.withName("ci:" + key);
	}
}
