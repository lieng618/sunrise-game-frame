package org.sunrise.game.game.logic.unit;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Position {
    private float x;
    private float y;
    private float z;
    private float orientation;

    public Position() {
    }

    public Position(float x, float y, float z, float orientation) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.orientation = orientation;
    }

    public void set(float x, float y, float z, float orientation) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.orientation = orientation;
    }
}
