package main.world;

import main.Main;
import main.world.entities.Entity;
import main.misc.*;
import main.world.entities.Illusion;
import main.world.entities.Lever;
import main.world.entities.MovingPlatform;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;
import processing.core.PVector;

import java.util.ArrayList;

public class Player extends Entity {

    public float velocity_y;

    private static final float WALK_SPEED = 3;
    private static final float JUMP_SPEED = -8;
    private static final float ACCELERATION_Y = 0.2f;
    private static final PVector SPRITE_SIZE = new PVector(39, 50);

    private static final float CAST_RADIUS = 250;

    private final Animator RUNE_ANIMATION;
    private final Animator WALK_ANIMATION;
    private final Animator CAST_ANIMATION;
    private final PImage JUMP_SPRITE;

    /**Allows the player to jump if they have just stepped off an edge, this is common in platformers.**/
    private final Timer COYOTE_TIMER;
    private final PVector DETECT_OFFSET;

    private float pastY1 = 0;
    private float pastY2 = 0;

    private boolean facingLeft;
    private boolean grounded;
    private boolean justCast;
    private PVector illusionPosition;

    private Entity standingOn;
    private Entity pastStandingOn;

    public Player(PApplet p, PVector position, World world) {
        super(p, world, new CollisionBox(p,
                new PVector(10, 10),
                new PVector(20, 40)
                ), position);

        RUNE_ANIMATION = new Animator(Main.animations.get("illusionPlayer"), 4, false);
        RUNE_ANIMATION.setEnded();
        WALK_ANIMATION = new Animator(Main.animations.get("walkPlayer"), 8);
        CAST_ANIMATION = new Animator(Main.animations.get("castPlayer"), 6, false);
        CAST_ANIMATION.setEnded();
        JUMP_SPRITE = Main.sprites.get("jumpPlayer");

        COYOTE_TIMER = new Timer(Utilities.secondsToFrames(0.3f), true);
        DETECT_OFFSET = new PVector(
                collider.getRightEdge() / 2,
                collider.getBottomEdge() + 1
        );
    }

    @Override
    public void update() {
        move();
        handleIllusions();
    }

    private void handleIllusions() {
        if (!CAST_ANIMATION.ended()) {
            CAST_ANIMATION.update();
        }
        if (InputManager.getInstance().leftMouse.falling() && standing() != null && CAST_ANIMATION.ended()) {
            CAST_ANIMATION.reset();
            illusionPosition = Main.matrixMousePosition.copy();
            justCast = false;
        }
        if (CAST_ANIMATION.getCurrentTime() == 2 && !justCast) {
            RUNE_ANIMATION.reset();
            justCast = true; //prevent casting on betweenFrames
            if (world.getCollidingEntities(new CollisionEntity(
                    standing().collider,
                    illusionPosition
            )).isEmpty() && PVector.dist(illusionPosition, position) < CAST_RADIUS)
                world.illusion = new Illusion(standing(), PVector.sub(illusionPosition, standing().position));
        }
    }

    private void move() {
        IntVector axes;
        if (CAST_ANIMATION.ended()) axes = Utilities.getAxesFromMovementKeys();
        else {
            axes = new IntVector(0, 0);
            facingLeft = Utilities.angleIsFacingLeftWeird(
                    Utilities.getAngle(
                            illusionPosition,
                            collider.getWorldCenter(position)
                    ) - PConstants.HALF_PI
            );
        }
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
        ArrayList<Entity> entities = world.getCollidingEntities(this);
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
                } else if (illusion.trueEntity instanceof Lever) {
                    if (!(((Lever) illusion.trueEntity).bottomedOut)) offset.direction = CollisionBox.Direction.None;
                }

            } else if (entity instanceof MovingPlatform) {
                MovingPlatform mp = (MovingPlatform) entity;
                speed = mp.getVelocity().x;
                groundVelocity_Y = mp.getVelocity().y;
            } else if (entity instanceof Lever) {
                if (!(((Lever) entity).bottomedOut)) offset.direction = CollisionBox.Direction.None;
            }

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
        PImage sprite;
        if (CAST_ANIMATION.ended()) sprite = WALK_ANIMATION.getCurrentFrame();
        else sprite = CAST_ANIMATION.getCurrentFrame();
        if (!grounded) sprite = JUMP_SPRITE;

        if (!CAST_ANIMATION.ended()) {
            RUNE_ANIMATION.update();
            float angle = Utilities.getAngle(collider.getWorldCenter(position), illusionPosition);
            angle -= PConstants.HALF_PI;
            PVector runePosition = PVector.add(
                    collider.getWorldCenter(position),
                    PVector.fromAngle(angle).setMag(50)
            );
            P.imageMode(PConstants.CENTER);
            P.image(RUNE_ANIMATION.getCurrentFrame(), runePosition.x, runePosition.y);
            P.imageMode(Main.DEFAULT_MODE);
            P.fill(175, 1, 175, 35);
            P.circle(position.x + collider.OFFSET.x / 2, position.y + collider.OFFSET.y / 2, CAST_RADIUS * 2);
        }

        if (facingLeft) { //mirroring
            P.pushMatrix();
            P.translate(position.x, position.y);
            P.scale(-1, 1);
            P.image(sprite, -SPRITE_SIZE.x, 0,
                    SPRITE_SIZE.x, SPRITE_SIZE.y);
            P.popMatrix();
        } else P.image(sprite, position.x, position.y,
                SPRITE_SIZE.x, SPRITE_SIZE.y);

        if (Main.debug) collider.display(position);

        if (standing() != null) standing().highlight();
    }
}
