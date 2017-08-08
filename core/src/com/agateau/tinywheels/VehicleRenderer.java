package com.agateau.tinywheels;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;

/**
 * Renders a vehicle
 */
public class VehicleRenderer implements Renderer {
    private final Assets mAssets;
    private final Vehicle mVehicle;
    private final Array<Renderer> mRenderers = new Array<Renderer>();
    private final SkidmarksRenderer mSkidmarksRenderer;

    public VehicleRenderer(Assets assets, Vehicle vehicle) {
        mAssets = assets;
        mVehicle = vehicle;
        mSkidmarksRenderer = new SkidmarksRenderer(mAssets);
    }

    public void addRenderer(Renderer renderer) {
        mRenderers.add(renderer);
    }

    public void removeRenderer(Renderer renderer) {
        mRenderers.removeValue(renderer, true);
    }

    public void draw(Batch batch, int zIndex) {
        if (zIndex == Constants.Z_GROUND) {
            for(Vehicle.WheelInfo info: mVehicle.getWheelInfos()) {
                mSkidmarksRenderer.draw(batch, info.wheel.getSkidmarks());
            }
            DrawUtils.drawBodyRegionShadow(batch, mVehicle.getBody(), mVehicle.getRegion());
            return;
        }
        if (zIndex != Constants.Z_VEHICLES) {
            return;
        }

        for(Vehicle.WheelInfo info: mVehicle.getWheelInfos()) {
            DrawUtils.drawBodyRegion(batch, info.wheel.getBody(), info.wheel.getRegion());
        }
        DrawUtils.drawBodyRegion(batch, mVehicle.getBody(), mVehicle.getRegion());

        if (mVehicle.getTurboTime() >= 0) {
            drawTurbo(batch);
        }

        for (Renderer renderer : mRenderers) {
            renderer.draw(batch, zIndex);
        }
    }

    private void drawTurbo(Batch batch) {
        TextureRegion region = mAssets.turboFlame.getKeyFrame(mVehicle.getTurboTime(), true);
        Body body = mVehicle.getBody();
        Vector2 center = body.getPosition();
        float angle = body.getAngle() * MathUtils.radiansToDegrees;
        float w = Constants.UNIT_FOR_PIXEL * region.getRegionWidth();
        float h = Constants.UNIT_FOR_PIXEL * region.getRegionHeight();
        float refH = mVehicle.getHeight() / 2;
        float x = center.x + refH * MathUtils.cosDeg(angle - 90);
        float y = center.y + refH * MathUtils.sinDeg(angle - 90);
        batch.draw(region,
                x - w / 2, y - h, // pos
                w / 2, h, // origin
                w, h, // size
                1, 1, // scale
                angle);
    }
}