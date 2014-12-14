package com.greenyetilab.race;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.PerformanceCounter;
import com.badlogic.gdx.utils.PerformanceCounters;
import com.greenyetilab.utils.log.NLog;

/**
 * Contains all the information and objects running in the world
 */
public class GameWorld implements ContactListener, Disposable {
    public enum State {
        RUNNING,
        BROKEN,
        FINISHED
    }

    private static final float TIME_STEP = 1f/60f;
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;

    private final MapInfo mMapInfo;
    private final TiledMap mMap;

    private final World mBox2DWorld;
    private final RaceGame mGame;
    private float mTimeAccumulator = 0;

    private PlayerVehicle mVehicle;
    private State mState = State.RUNNING;

    private Vector2[] mSkidmarks = new Vector2[4000];
    private int mSkidmarksIndex = 0;
    private Array<GameObject> mActiveGameObjects = new Array<GameObject>();
    private float mScore = 0;

    private final PerformanceCounter mBox2DPerformanceCounter;
    private final PerformanceCounter mGameObjectPerformanceCounter;

    public GameWorld(RaceGame game, MapInfo mapInfo, PerformanceCounters performanceCounters) {
        mGame = game;
        mBox2DWorld = new World(new Vector2(0, 0), true);
        mBox2DWorld.setContactListener(this);
        mMapInfo = mapInfo;
        mMap = mapInfo.getMap();

        mBox2DPerformanceCounter = performanceCounters.add("- box2d");
        mGameObjectPerformanceCounter = performanceCounters.add("- g.o");
        setupSled();
        setupOutsideWalls();
        setupObjects();

        Mine mine = new Mine();
        mine.init(this, game.getAssets(), mMapInfo.getTileWidth() * 10, mMapInfo.getTileHeight() * 10);
        addGameObject(mine);
    }

    public TiledMap getMap() {
        return mMap;
    }

    public MapInfo getMapInfo() {
        return mMapInfo;
    }

    public World getBox2DWorld() {
        return mBox2DWorld;
    }

    public Vehicle getVehicle() {
        return mVehicle;
    }

    public int getScore() {
        return (int)mScore;
    }

    public void increaseScore(int delta) {
        NLog.i("+%d", delta);
        mScore += delta;
    }

    public Vector2[] getSkidmarks() {
        return mSkidmarks;
    }

    public Array<GameObject> getActiveGameObjects() {
        return  mActiveGameObjects;
    }

    public void addGameObject(GameObject object) {
        mActiveGameObjects.add(object);
    }

    public void removeGameObject(GameObject object) {
        mActiveGameObjects.removeValue(object, true);
    }

    public boolean isVisible(float x, float y) {
        float dy = Math.abs(y - mVehicle.getY());
        return dy < Constants.VIEWPORT_WIDTH * 1.2f;
    }

    public void act(float delta) {
        float oldY = mVehicle.getY();

        mBox2DPerformanceCounter.start();
        // fixed time step
        // max frame time to avoid spiral of death (on slow devices)
        float frameTime = Math.min(delta, 0.25f);
        mTimeAccumulator += frameTime;
        while (mTimeAccumulator >= TIME_STEP) {
            mBox2DWorld.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            mTimeAccumulator -= TIME_STEP;
        }
        mBox2DPerformanceCounter.stop();

        float deltaY = mVehicle.getY() - oldY;
        if (delta > 0) {
            mScore += deltaY * Constants.SCORE_PER_METER;
        }

        mGameObjectPerformanceCounter.start();
        // FIXME: Use SnapshotArray if game objects ever gain access to removing items from mActiveGameObjects
        for (int idx = mActiveGameObjects.size - 1; idx >= 0; --idx) {
            GameObject obj = mActiveGameObjects.get(idx);
            if (!obj.act(delta)) {
                mActiveGameObjects.removeIndex(idx);
            }
        }
        mGameObjectPerformanceCounter.stop();
    }

    private void setupSled() {
        Vector2 position = findStartTilePosition();
        assert(position != null);

        // Car
        TextureRegion carRegion = mGame.getAssets().atlas.findRegion("sled/sled");
        TextureRegion wheelRegion = mGame.getAssets().atlas.findRegion("sled/sled-ski");
        mVehicle = new PlayerVehicle(carRegion, this, position);
        mVehicle.setLimitAngle(true);
        //mVehicle.setCorrectAngle(true);

        // Wheels
        final float REAR_WHEEL_Y = Constants.UNIT_FOR_PIXEL * 16f;
        final float WHEEL_BASE = Constants.UNIT_FOR_PIXEL * 46f;

        float wheelW = Constants.UNIT_FOR_PIXEL * wheelRegion.getRegionWidth();
        float rightX = Constants.UNIT_FOR_PIXEL * carRegion.getRegionWidth() / 2 - wheelW / 2 + 0.05f;
        float leftX = -rightX;
        float rearY = Constants.UNIT_FOR_PIXEL * -carRegion.getRegionHeight() / 2 + REAR_WHEEL_Y;
        float frontY = rearY + WHEEL_BASE + 0.2f;

        Vehicle.WheelInfo info;
        info = mVehicle.addWheel(wheelRegion, 0, frontY);
        info.steeringFactor = 1;
        info = mVehicle.addWheel(wheelRegion, leftX, rearY);
        //info.wheel.setCanDrift(true);
        info = mVehicle.addWheel(wheelRegion, rightX, rearY);
        //info.wheel.setCanDrift(true);

        addGameObject(mVehicle);
    }

    private Vector2 findStartTilePosition() {
        TiledMapTileLayer layer = (TiledMapTileLayer) mMap.getLayers().get("Centers");
        int tx = 0;
        for (; tx < layer.getWidth(); ++tx) {
            TiledMapTileLayer.Cell cell = layer.getCell(tx, 0);
            if (cell != null) {
                break;
            }
        }
        float tw = Constants.UNIT_FOR_PIXEL * layer.getTileWidth();
        float th = Constants.UNIT_FOR_PIXEL * layer.getTileHeight();
        return new Vector2(tx * tw + tw / 2, th);
    }

    private void setupOutsideWalls() {
        TiledMapTileLayer layer = (TiledMapTileLayer) mMap.getLayers().get(0);
        float mapWidth = Constants.UNIT_FOR_PIXEL * layer.getWidth() * layer.getTileWidth();
        float mapHeight = Constants.UNIT_FOR_PIXEL * layer.getHeight() * layer.getTileHeight();
        float wallSize = 1;
        // bottom
        Box2DUtils.createStaticBox(mBox2DWorld, 0, -wallSize, mapWidth, wallSize);
        // top
        Box2DUtils.createStaticBox(mBox2DWorld, 0, mapHeight, mapWidth, wallSize);
        // left
        Box2DUtils.createStaticBox(mBox2DWorld, -wallSize, 0, wallSize, mapHeight);
        // right
        Box2DUtils.createStaticBox(mBox2DWorld, mapWidth, 0, wallSize, mapHeight);
    }

    private void setupObjects() {
        MapLayer obstacleLayer = mMapInfo.getObstaclesLayer();
        TiledMapTileLayer wallsLayer = mMapInfo.getWallsLayer();
        ObstacleCreator creator = new ObstacleCreator(this, mGame.getAssets(), mMap.getTileSets(), wallsLayer);
        for (MapObject object : obstacleLayer.getObjects()) {
            creator.create(object);
        }
    }

    public void addSkidmarkAt(Vector2 position) {
        Vector2 pos = mSkidmarks[mSkidmarksIndex];
        if (pos == null) {
            pos = new Vector2();
            mSkidmarks[mSkidmarksIndex] = pos;
        }
        pos.x = position.x;
        pos.y = position.y;
        mSkidmarksIndex = (mSkidmarksIndex + 1) % mSkidmarks.length;
    }

    @Override
    public void beginContact(Contact contact) {
        Object userA = contact.getFixtureA().getBody().getUserData();
        Object userB = contact.getFixtureB().getBody().getUserData();
        if (userA instanceof Collidable) {
            ((Collidable) userA).beginContact(contact, contact.getFixtureB());
        }
        if (userB instanceof Collidable) {
            ((Collidable) userB).beginContact(contact, contact.getFixtureA());
        }
    }

    @Override
    public void endContact(Contact contact) {
        Object userA = contact.getFixtureA().getBody().getUserData();
        Object userB = contact.getFixtureB().getBody().getUserData();
        if (userA instanceof Collidable) {
            ((Collidable) userA).endContact(contact, contact.getFixtureB());
        }
        if (userB instanceof Collidable) {
            ((Collidable) userB).endContact(contact, contact.getFixtureA());
        }
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {

    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {

    }

    public State getState() {
        return mState;
    }

    public void setState(State state) {
        mState = state;
    }

    @Override
    public void dispose() {
        mMapInfo.dispose();
    }

}
