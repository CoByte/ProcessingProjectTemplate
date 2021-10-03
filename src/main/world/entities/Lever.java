package main.world.entities;

import main.Main;
import main.misc.CollisionBox;
import main.misc.CollisionEntity;
import main.misc.Trigger;
import main.world.Player;
import main.world.World;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;
import processing.core.PVector;

import java.util.ArrayList;

public class Lever extends Entity {
    private final LeverDoor door;

    public final float startingY;
    public final float endingY;

    private final CollisionEntity detection;

    private final PImage handleSprite;
    private final PImage baseSprite;

    public boolean bottomedOut = false;

    public Trigger pressed;

    public Lever(PApplet p, World world, PVector position, LeverDoor door) {
        super(p, world, new CollisionBox(
                p, new PVector(25, 5)
        ), position);

        this.door = door;

        detection = new CollisionEntity(
                new CollisionBox(p, new PVector(0, -5), new PVector(25, 5)),
                position);

        startingY = position.y;
        endingY = startingY + 25;

        pressed = new Trigger();

        handleSprite = Main.sprites.get("leverHandle");
        baseSprite = Main.sprites.get("leverBack");
    }

    @Override
    public void update() {
        super.update();

        move(
                getMovement(world, this, collider, position),
                !getDetected(world, detection)
        );

        bottomedOut = position.y >= endingY;
        pressed.triggerState(bottomedOut);

        if (pressed.rising()) door.activeLevers += 1;
        if (pressed.falling()) door.activeLevers -= 1;
    }

    public void move(float movement, boolean detected) {
        if (movement > 0) {
            position.y = Math.min(endingY, Math.max(position.y, position.y + movement));
        } else if (detected) {
            position.y = Math.min(endingY, Math.max(startingY, position.y - 1));
        }
    }

    @Override
    public void draw() {
        P.image(handleSprite, position.x, position.y, collider.SIZE.x, collider.SIZE.y);
    }

    public void drawBack() {
        P.imageMode(PConstants.CENTER);
        float size = (endingY - startingY) * 2f;
        P.image(baseSprite,
                collider.getWorldCenter(position).x,
                startingY + (endingY - startingY) / 2,
                size, size
        );
        P.imageMode(Main.DEFAULT_MODE);
    }

    public static boolean getDetected(World world, CollisionEntity detection) {
        ArrayList<Entity> detectedEntities = world.getCollidingEntities(detection);
        for (Entity e : detectedEntities) {
            if (e instanceof Player || e instanceof MovingPlatform) {
                return true;
            }
        }
        return false;
    }

    public static float getMovement(World world, Entity entity, CollisionBox collider, PVector position) {
        ArrayList<Entity> collided = world.getCollidingEntities(entity);
        float movement = 0;
        for (Entity other : collided) {
            if (!(other instanceof Player || other instanceof MovingPlatform || other instanceof Illusion)) { continue; }

            CollisionBox.Collision offset = collider.calculateOffset(position, other.position, other.collider);

            if (offset.direction != CollisionBox.Direction.Up) { continue; }

            if (other instanceof Player) {
                if (((Player) other).velocity_y <= 0) {
                    continue;
                }
            }

            if (other instanceof MovingPlatform) {
                if (((MovingPlatform) other).goingUp) {
                    continue;
                }
            }

            if ((other instanceof Illusion && ((Illusion) other).trueEntity instanceof MovingPlatform)) {
                if (((MovingPlatform) (((Illusion) other).trueEntity)).goingUp) {
                    continue;
                }
            }

            movement += offset.offset;
        }
        return movement;
    }
}
