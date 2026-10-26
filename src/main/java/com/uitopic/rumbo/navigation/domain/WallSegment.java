package com.uitopic.rumbo.navigation.domain;
import java.util.UUID;
public record WallSegment(UUID floorId, double ax, double ay, double bx, double by) {
  /** Strict line crossing; touching at endpoints and collinear segments are not 'through-wall'. */
  public boolean properlyCrosses(double cx, double cy, double dx, double dy) {
    double eps = 1.0e-9;
    double o1 = cross(ax, ay, bx, by, cx, cy), o2 = cross(ax, ay, bx, by, dx, dy);
    double o3 = cross(cx, cy, dx, dy, ax, ay), o4 = cross(cx, cy, dx, dy, bx, by);
    return ((o1 > eps && o2 < -eps) || (o1 < -eps && o2 > eps))
        && ((o3 > eps && o4 < -eps) || (o3 < -eps && o4 > eps));
  }
  private static double cross(double ax,double ay,double bx,double by,double cx,double cy) {
    return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
  }
}
