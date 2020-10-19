/*
 * Copyright (C) 2011-2018 Rinde R.S. van Lon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.rinsim.examples.taxi;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.MoveProgress;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModels;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.GeomHeuristics;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import java.util.Date;

import javax.measure.Measure; 



/**
 * Implementation of a very simple taxi agent. It moves to the closest customer,
 * picks it up, then delivers it, repeat.
 *
 * @author Rinde van Lon
 */
class Taxi extends Vehicle {
  private static final double SPEED = 1000d;
  private static final double  scale = 1000000 / (8 * 1.425139046);
  private static final double METER_TO_KM = 1/1000d;
  private Optional<Parcel> curr;
  private long taxiId;

  Taxi(Point startPosition, int capacity, long id, double speed) {
    super(VehicleDTO.builder()
      .capacity(capacity)
      .startPosition(startPosition)
      .speed(speed)
      .build());
    curr = Optional.absent();
    taxiId = id;
  }

  @Override
  public void afterTick(TimeLapse timeLapse) {}

  @Override
  protected void tickImpl(TimeLapse time) {
    final RoadModel rm = getRoadModel();
    final PDPModel pm = getPDPModel();

    if (!time.hasTimeLeft()) {
      return;
    }
    if (!curr.isPresent()) {
      curr = Optional.fromNullable(RoadModels.findClosestObject(
        rm.getPosition(this), rm, Parcel.class));
    }

    if (curr.isPresent()) {
      final boolean inCargo = pm.containerContains(this, curr.get());
      // sanity check: if it is not in our cargo AND it is also not on the
      // RoadModel, we cannot go to curr anymore.
      if (!inCargo && !rm.containsObject(curr.get())) {
        curr = Optional.absent();
      } else if (inCargo) {
        // if it is in cargo, go to its destination
    	  MoveProgress mp = rm.moveTo(this, curr.get().getDeliveryLocation(), time);
        if (rm.getPosition(this).equals(curr.get().getDeliveryLocation())) {
          // deliver when we arrive
          pm.deliver(this, curr.get(), time);
          String deliverLine = TaxiExample2.SIMPLE_DATE_FORMAT.format(new Date(time.getTime())) + "," + 
        		  toLat(rm.getPosition(this).y) + "," + toLon(rm.getPosition(this).x);
          String distanceLine = rm.getDistanceOfPath(
        		  rm.getShortestPathTo(
        				  this.curr.get().getPickupLocation(),
        				  this.curr.get().getDeliveryLocation()
        				  )
        		  ).getValue().toString();
    	  System.out.println(TaxiExample2.containerCurrentTripId.get(this) + "," + deliverLine + "," + distanceLine);
        }
      } else {
        // it is still available, go there as fast as possible
        rm.moveTo(this, curr.get(), time);
        if (rm.equalPosition(this, curr.get())) {
          // pickup customer
          pm.pickup(this, curr.get(), time);
          String pickupLine = TaxiExample2.tripIndex + "," + this.taxiId + "," + 
    			  TaxiExample2.SIMPLE_DATE_FORMAT.format(new Date(time.getTime())) + "," + 
    			  toLat(rm.getPosition(this).y) + "," + toLon(rm.getPosition(this).x);
          TaxiExample2.containerCurrentTripId.put(this, pickupLine);
    	  TaxiExample2.tripIndex++;
        }
      }
    }
  }

	private double toLat(double y) {
		return Math.toDegrees(  Math.atan(asinh(1.0 /Math.toRadians(y/ (scale * METER_TO_KM) ))));
	}
	
	private double toLon(double x) {
		return x / METER_TO_KM / scale;
	}
	double asinh(double x) {
	    return Math.log(x + Math.sqrt(x*x + 1.0));
	}
}
