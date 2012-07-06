package org.terasology.entitySystem.metadata.extension;

import org.terasology.entitySystem.metadata.AbstractTypeHandler;
import org.terasology.protobuf.EntityData;

import javax.vecmath.Vector2f;

/**
 * @author Immortius <immortius@gmail.com>
 */
public class Vector2fTypeHandler extends AbstractTypeHandler<Vector2f> {

    public EntityData.Value serialize(Vector2f value) {
        return EntityData.Value.newBuilder().addFloat(value.x).addFloat(value.y).build();
    }

    public Vector2f deserialize(EntityData.Value value) {
        if (value.getFloatCount() > 1) {
            return new Vector2f(value.getFloat(0), value.getFloat(1));
        }
        return null;
    }

    public Vector2f copy(Vector2f value) {
        if (value != null) {
            return new Vector2f(value);
        }
        return null;
    }
}
