package main.world;

import main.Main;
import main.world.entities.Entity;
import main.misc.*;
import main.world.entities.Illusion;
import main.world.entities.MovingPlatform;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;

public class Player extends Entity {

    private static final float WALK_SPEED = 3;
    private static final float JUMP_SPEED = -8;
    private static final float ACCELERATION_Y = 0.2f;

    private final World WORLD;
    private final Animator WALK_ANIMATION;
    /**Allows the player to jump if they have just stepped off an edge, this is common in platformers.**/
    private final Timer COYOTE_TIMER;
    private final PVector DETECT_OFFSET;

    private float pastY1 = 0;
    private float pastY2 = 0;

    private boolean facingLeft;
    private boolean grounded;
    private float velocity_y;

    private Entity standingOn;
    private Entity pastStandingOn;

    public Player(PApplet p, PVector position, World world) {
        super(p, new CollisionBox(p, new PVector(39, 50)/*, new PVector(0, 2)*/), position);

        WORLD = world;
        WALK_ANIMATION = new Animator(Main.animations.get("walkPlayer"), 8);
        COYOTE_TIMER = new Timer(Utilities.secondsToFrames(0.3f), true);
        DETECT_OFFSET = new PVector(
                collider.getRightEdge() / 2,
                collider.getBottomEdge() + 5
        );
    }

    @Override
    public void update() {
        move();
        handleIllusions();
    }

    private void handleIllusions() {
        if (InputManager.getInstance().leftMouse.falling() && standing() != null) {
            WORLD.illusion = new Illusion(standing(), PVector.sub(Main.matrixMousePosition, standing().position));
        }
    }

    private void move() {
        IntVector axes = Utilities.getAxesFromMovementKeys();
        /*
        I use this instead of `facingLeft = axes.x < 0` because I don't want the player's
        direction to change if they stop moving (axes.x == 0).
        */
        if (axes.x < 0) facingLeft = true;
        else if (axes.x > 0) facingLeft = false;
        float groundVelocity_Y = 0;

        //collision with ground
        if (axes.y < 0 && (grounded || !COYOTE_TIMER.triggered(false))) {
            velocity_y = JUMP_SPEED;
            COYOTE_TIMER.setCurrentTime(COYOTE_TIMER.getAlarmTime());
        }
        velocity_y += ACCELERATION_Y;
        if (axes.x != 0 && grounded) WALK_ANIMATION.update();

        position.x += axes.x * WALK_SPEED;
        position.y += velocity_y;

        grounded = false;
        pastStandingOn = standingOn;
        standingOn = null;
        ArrayList<Entity> entities = WORLD.getCollidingEntities(this);
//        System.out.println(entities);
        for (Entity entity : entities) {
            CollisionBox otherCollider = entity.collider;
            CollisionBox.Collision offset = collider.calculateOffset(position, entity.position, otherCollider);
            float speed = 0;
            if (entity instanceof Illusion) {
                Illusion illusion = (Illusion) entity;
                offset = collider.calculateOffset(position, illusion.getPosition(), illusion.getCollider());
                if (illusion.trueEntity instanceof MovingPlatform) {
                    MovingPlatform mp = (MovingPlatform) ((Illusion) entity).trueEntity;
                    speed = mp.getVelocity().x;
                    groundVelocity_Y = mp.getVelocity().y;
                }
            }
            if (entity instanceof MovingPlatform) {
                MovingPlatform mp = (MovingPlatform) entity;
                speed = mp.getVelocity().x;
                groundVelocity_Y = mp.getVelocity().y;
            }
//            System.out.println(offset);
            switch (offset.direction) {
                case Up:
                    position.y += offset.offset;
                    //velocity_y = Math.max(0, velocity_y);
                    break;
                case Down:
                    position.y -= offset.offset;
                    if (velocity_y > 0) grounded = true;
                    position.x += speed;
                    break;
                case Left: position.x += offset.offset; break;
                case Right: position.x -= offset.offset; break;
            }

            if (otherCollider.pointIsInsideBox(entity.position, PVector.add(position, DETECT_OFFSET))
                    && !(entity instanceof Illusion)) {
                standingOn = entity;
            }
        }
        if (grounded) COYOTE_TIMER.reset();
        COYOTE_TIMER.update();

        if (grounded && velocity_y > groundVelocity_Y) velocity_y = groundVelocity_Y;
        if (pastY2 == pastY1 && pastY1 == position.y && !grounded) {
            velocity_y = 0;
        }

        pastY2 = pastY1;
        pastY1 = position.y;

//        System.out.println(standing());
    }

    public Entity standing() {
        return standingOn != null ? standingOn : pastStandingOn;
    }

    @Override
    public void draw() {
        if (facingLeft) { //mirroring
            P.pushMatrix();
            P.translate(position.x, position.y);
            P.scale(-1, 1);
            P.image(WALK_ANIMATION.getCurrentFrame(), -collider.getRightEdge(), 0,
                    collider.getRightEdge(), collider.getBottomEdge());
            P.popMatrix();
        } else P.image(WALK_ANIMATION.getCurrentFrame(), position.x, position.y,
                collider.getRightEdge(), collider.getBottomEdge());

        if (Main.debug) collider.display(position);

        if (standingOn != null) standingOn.highlight();
    }
}
