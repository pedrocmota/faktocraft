package com.faktocraft.common.block.impl.quarry;

public final class GantryHeadMotion {
  public static final double RESYNC_DISTANCE = 1.0;
  public static final double CATCH_UP_BOOST = 0.5;

  private GantryHeadMotion() {
  }

  public static void predict(double[] synced, double[] goal, double speed, double elapsed, double[] out) {
    double dx = goal[0] - synced[0];
    double dy = goal[1] - synced[1];
    double dz = goal[2] - synced[2];
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    double travel = speed * Math.max(0.0, elapsed);
    if (distance <= travel || distance == 0.0) {
      out[0] = goal[0];
      out[1] = goal[1];
      out[2] = goal[2];
      return;
    }
    out[0] = synced[0] + dx / distance * travel;
    out[1] = synced[1] + dy / distance * travel;
    out[2] = synced[2] + dz / distance * travel;
  }

  public static void follow(double[] current, double[] predicted, double speed, double elapsed) {
    double dx = predicted[0] - current[0];
    double dy = predicted[1] - current[1];
    double dz = predicted[2] - current[2];
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance == 0.0) {
      return;
    }
    if (distance > RESYNC_DISTANCE) {
      current[0] = predicted[0];
      current[1] = predicted[1];
      current[2] = predicted[2];
      return;
    }
    double step = speed * elapsed * (1.0 + CATCH_UP_BOOST * Math.min(1.0, distance));
    if (distance <= step) {
      current[0] = predicted[0];
      current[1] = predicted[1];
      current[2] = predicted[2];
      return;
    }
    current[0] += dx / distance * step;
    current[1] += dy / distance * step;
    current[2] += dz / distance * step;
  }
}
