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


import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.newLinkedHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.math3.distribution.MixtureMultivariateNormalDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.MathArrays;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.Container;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.time.ClockController;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.examples.taxi.TaxiRenderer.Language;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.MultiAttributeData;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.io.DotGraphIO;
import com.github.rinde.rinsim.geom.io.Filters;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

import java.text.DateFormat;
import java.text.SimpleDateFormat; 


/**
 * Example showing a fleet of taxis that have to pickup and transport customers
 * around the city of Leuven.
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * @author Rinde van Lon
 */
public final class TaxiExample2 {
  
  public static final DateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd HH:mm:ss"); 
  
  public static Map<Container, String> containerCurrentTripId = newLinkedHashMap();
  public static long tripIndex = 0;
  private static long END_TIME = 24 * 60 * 60 * 1000L; //duration of simulation 

  private static final int NUM_DEPOTS = 0;
  private static int NUM_TAXIS = 4; //number of taxi in the simulation 
  private static int NUM_CUSTOMERS = 5; //initial number of customers 
  
  private static long RANDOM_SEED = 123L;
  private static long TICK_LENGTH = 1000L;//small ticking time --> takes too much time
  // time in ms
  private static final long SERVICE_DURATION = 60000;//pickup and deliver operation duration 
  private static final int TAXI_CAPACITY = 5; 
  private static final int DEPOT_CAPACITY = 100;

  private static final int SPEED_UP = 4;
  private static final int MAX_CAPACITY = 3;
  private static double NEW_CUSTOMER_PROB = .01;//probability to generate a new customer each time tick

//  private static final String MAP_FILE = "/data/maps/leuven-simple.dot";
  private static String MAP_FILE = "/home/abdu/eclipse-workspace/test1.dot";
  private static PrintStream file_out, standard_out;
  private static final Map<String, Graph<MultiAttributeData>> GRAPH_CACHE =
    newHashMap();

  private static final long TEST_STOP_TIME = 60 * 1000;
  private static final int TEST_SPEED_UP = 1;

  private TaxiExample2() {}

  /**
   * Starts the {@link TaxiExample2}.
   * @param args The first option may optionally indicate the end time of the
   *          simulation.
   */
  public static void main(@Nullable String[] args) {
	  System.out.println("tripdatagenerator_v20200818");
	  if (args.length == 6) {
		  MAP_FILE = args[0];
		  NUM_TAXIS = Integer.parseInt(args[1]);
		  NUM_CUSTOMERS = Integer.parseInt(args[2]);
		  NEW_CUSTOMER_PROB = Double.parseDouble(args[3]);
		  END_TIME = Long.parseLong(args[4]) * 60 * 60 * 1000;
		  TICK_LENGTH = Long.parseLong(args[5]);
		  
		}
	  
//    final long endTime = args != null && args.length >= 1 ? Long
//      .parseLong(args[0]) : END_TIME;
	  final long endTime = END_TIME;
//
//    final String graphFile = args != null && args.length >= 2 ? args[1]
//      : MAP_FILE;
	final String graphFile = MAP_FILE;
	try {
		file_out = new PrintStream(MAP_FILE+".csv");
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	standard_out = System.out;
	long startExcutionTime = System.currentTimeMillis();
    run(false, endTime, graphFile, null /* new Display() */, null, null);
    long endExcutionTime = System.currentTimeMillis();
    System.setOut(standard_out);
    System.out.println("\nexecution time (s) = " + ((endExcutionTime  - startExcutionTime )/1000) ); 

  }

  /**
   * Run the example.
   * @param testing If <code>true</code> enables the test mode.
   */
  public static void run(boolean testing) {
    run(testing, Long.MAX_VALUE, MAP_FILE, null, null, null);
  }

  /**
   * Starts the example.
   * @param testing Indicates whether the method should run in testing mode.
   * @param endTime The time at which simulation should stop.
   * @param graphFile The graph that should be loaded.
   * @param display The display that should be used to show the ui on.
   * @param m The monitor that should be used to show the ui on.
   * @param list A listener that will receive callbacks from the ui.
   * @return The simulator instance.
   */
  public static Simulator run(boolean testing, final long endTime,
      String graphFile,
      @Nullable Display display, @Nullable Monitor m, @Nullable Listener list) {
	  
	  final View.Builder view = createGui(testing, display, m, list);

    // use map of leuven
    final Simulator simulator = Simulator.builder()
      .addModel(RoadModelBuilders.staticGraph(loadGraph(graphFile)))
//    		.addModel(RoadModelBuilders.plane())
    		.addModel(DefaultPDPModel.builder())
//      .setRandomGenerator(new GaussianRandomGenerator(new MersenneTwister(123L)))
    		.setRandomSeed(RANDOM_SEED)
    		.setTickLength(TICK_LENGTH)
//      .addModel(view)
      .build();
    final RandomGenerator rng = simulator.getRandomGenerator();
    NormalDistribution nd = new NormalDistribution(rng, 0, 1);
    

    
//    for (int i = 0; i < 100000; i++) {
////    	System.out.println(rng.nextGaussian());
//    	System.out.println(gmm.sample()[0]);
//	}
    

    final RoadModel roadModel = simulator.getModelProvider().getModel(
      RoadModel.class);
    // add depots, taxis and parcels to simulator
    for (int i = 0; i < NUM_DEPOTS; i++) {
      simulator.register(new TaxiBase(roadModel.getRandomPosition(rng),
        DEPOT_CAPACITY));
    }
    System.out.println("initialising " + NUM_TAXIS + " taxis...");
    for (int i = 0; i < NUM_TAXIS; i++) {
      simulator.register(new Taxi(roadModel.getRandomPosition(rng),
        TAXI_CAPACITY, i, 200000));
    }
    System.out.println("initialising " + NUM_CUSTOMERS + " customers...");
    for (int i = 0; i < NUM_CUSTOMERS; i++) {
      simulator.register(new Customer(
        Parcel.builder(roadModel.getRandomPosition(rng),
          roadModel.getRandomPosition(rng))
          .serviceDuration(SERVICE_DURATION)
          .neededCapacity(1 + rng.nextInt(MAX_CAPACITY))
          .buildDTO()));
    }

    simulator.addTickListener(new TickListener() {
      @Override
      public void tick(TimeLapse time) {
    	  if (time.getStartTime()% (endTime / 100) == 0) {
    		  System.setOut(standard_out);
    		  System.out.print("\r" + (time.getStartTime()*100 / endTime) +"%");
    		  System.setOut(file_out);
    		  }
        if (time.getStartTime() > endTime) {
          simulator.stop();
        } else if (rng.nextDouble() < NEW_CUSTOMER_PROB) {
          //System.out.println("new customer");
        	simulator.register(new Customer(
            Parcel
              .builder(roadModel.getRandomPosition(rng),
                roadModel.getRandomPosition(rng))
              .serviceDuration(SERVICE_DURATION)
              .neededCapacity(1 + rng.nextInt(MAX_CAPACITY))
              .buildDTO()));
        }
      }

      @Override
      public void afterTick(TimeLapse timeLapse) {}
    });

    
    System.out.println("ticking...");
    
    System.setOut(file_out);
    
    System.out.println("tripId, taxiId, PU_timeStamp, PU_lat, PU_long, DO_timeStamp, DO_lat, DO_long, distance(km)");

    simulator.start();//if no GUI then clock.start() is called    

    return simulator;
  }

  static View.Builder createGui(
      boolean testing,
      @Nullable Display display,
      @Nullable Monitor m,
      @Nullable Listener list) {

    View.Builder view = View.builder()
      .with(GraphRoadModelRenderer.builder())
      .with(RoadUserRenderer.builder()
        .withImageAssociation(
          TaxiBase.class, "/graphics/perspective/tall-building-64.png")
        .withImageAssociation(
          Taxi.class, "/graphics/flat/taxi-32.png")
        .withImageAssociation(
          Customer.class, "/graphics/flat/person-red-32.png"))
      .with(TaxiRenderer.builder(Language.ENGLISH))
      .withTitleAppendix("Taxi example");

    if (testing) {
      view = view.withAutoClose()
        .withAutoPlay()
        .withSimulatorEndTime(TEST_STOP_TIME)
        .withSpeedUp(TEST_SPEED_UP);
    } else if (m != null && list != null && display != null) {
      view = view.withMonitor(m)
        .withSpeedUp(SPEED_UP)
        .withResolution(m.getClientArea().width, m.getClientArea().height)
        .withDisplay(display)
        .withCallback(list)
        .withAsync()
        .withAutoPlay()
        .withAutoClose();
    }
    return view;
  }

  // load the graph file
  static Graph<MultiAttributeData> loadGraph(String name) {
    try {
      if (GRAPH_CACHE.containsKey(name)) {
        return GRAPH_CACHE.get(name);
      }
//      final Graph<MultiAttributeData> g = DotGraphIO
//        .getMultiAttributeGraphIO(
//          Filters.selfCycleFilter())
//        .read(
//          TaxiExample.class.getResourceAsStream(name));
      final Graph<MultiAttributeData> g = DotGraphIO
    	        .getMultiAttributeGraphIO(
    	          Filters.selfCycleFilter())
    	        .read(new FileInputStream(new File(name)));
      GRAPH_CACHE.put(name, g);
      return g;
    } catch (final FileNotFoundException e) {
      throw new IllegalStateException(e);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A customer with very permissive time windows.
   */
  static class Customer extends Parcel {
    Customer(ParcelDTO dto) {
      super(dto);
    }

    @Override
    public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
  }

  // currently has no function
  static class TaxiBase extends Depot {
    TaxiBase(Point position, double capacity) {
      super(position);
      setCapacity(capacity);
    }

    @Override
    public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
  }

}
