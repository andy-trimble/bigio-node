/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.a2i.sim.test;

import com.a2i.sim.Parameters;
import com.a2i.sim.Speaker;
import com.a2i.sim.Starter;
import com.a2i.sim.core.MessageListener;
import com.a2i.sim.core.codec.GenericEncoder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author atrimble
 */
public class Latency {

    private static final Logger LOG = LoggerFactory.getLogger(Latency.class);
    
    @Autowired
    private Speaker speaker;

    private boolean running = true;
    private boolean headerPrinted = false;
    private int messageCount = 0;
    private final int throwAway = 100;
    private final int sampleSize = 10000;

    private final List<Long> latencies = new ArrayList<>(sampleSize);

    private final long clockOverhead;

    private int currentMessageIndex = 0;
    private final int initialBytes = 16;
    private final int maxBytes = 16384 + 1;

    private final List<LatencyMessage> messages = new ArrayList<>();

    private LatencyMessage currentMessage;

    private boolean seeded = false;

    Thread injectThread = new Thread() {
        @Override
        @SuppressWarnings("SleepWhileInLoop")
        public void run() {
            while(!seeded && running) {
                try {
                    Thread.sleep(1000l);
                    LOG.info("Seeding");
                    currentMessage.sendTime = System.nanoTime();
                    speaker.send("HelloWorldConsumer", currentMessage);
                } catch(Exception ex) {
                    LOG.debug("Error", ex);
                }
            }
        }
    };

    public Latency() {
        this.speaker = Starter.bootstrap();

        int currentBytes = initialBytes;
        while(currentBytes < maxBytes) {
            StringBuilder padding = new StringBuilder();
            for(int i = 0; i < currentBytes - 12; ++i) {
                padding.append('a');
            }
            if(currentBytes < 64) {
                padding.append("aa");
            }
            LatencyMessage m = new LatencyMessage();
            m.padding = padding.toString();
            messages.add(m);
            currentBytes = currentBytes * 2;
        }

        for(int i = 0; i < sampleSize; ++i) {
            latencies.add(0l);
        }
        latencies.clear();

//        int currentBytes = maxBytes;
//        while(currentBytes >= initialBytes) {
//            StringBuilder padding = new StringBuilder();
//            for(int i = 0; i < currentBytes - 12; ++i) {
//                padding.append('a');
//            }
//            LatencyMessage m = new LatencyMessage();
//            m.padding = padding.toString();
//            messages.add(m);
//            currentBytes = currentBytes / 2;
//        }

        currentMessageIndex = 0;
        currentMessage = messages.get(currentMessageIndex);
        
        long startTime;
        long finishTime = 0;
        
        startTime = System.nanoTime();
        for (int i = 0; i < 16; ++i) {
            finishTime = System.nanoTime();
        }
        clockOverhead = (finishTime - startTime) / 16;
            
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                running = false;
                
                try {
                    injectThread.join();
                } catch(InterruptedException ex) {
                    ex.printStackTrace();
                }

//                printStats();
            }
        });
    }

    private void printStats() {
        Collections.sort(latencies);

        long sum = 0;
        for(long l : latencies) {
            sum += l;
        }
        int sampleSize = latencies.size();
        double average = (double)sum / sampleSize;
        double deviationSum = 0;
        for(long l : latencies) {
            deviationSum += (l - average) * (l - average);
        }
        double deviation = deviationSum / (sum / sampleSize - 1);
        deviation = Math.sqrt(deviation);

        int percentile_50_sample_index = (50 * sampleSize) / 100;
        int percentile_90_sample_index = (90 * sampleSize) / 100;
        int percentile_99_sample_index = (99 * sampleSize) / 100;
        int percentile_9999_sample_index = 
            (9999 * sampleSize) / 10000;

        average /= 1000.0; //convert to usec
        deviation /= 1000.0; //convert to usec
        double min_sample = (double)
            latencies.
            get(0)/1000.0;
        double max_sample = (double)
            latencies.
            get(sampleSize-1)/1000.0;
        double percentile_50_sample = (double)
            latencies.
            get(percentile_50_sample_index)/1000.0;
        double percentile_90_sample = (double)
            latencies.
            get(percentile_90_sample_index)/1000.0;
        double percentile_99_sample = (double)
            latencies.
            get(percentile_99_sample_index)/1000.0;
        double percentile_9999_sample = (double)
            latencies.
            get(percentile_9999_sample_index)/1000.0;

        int length = 0;
        
        try {
            length = GenericEncoder.encode(currentMessage).length;
        } catch(IOException ex) {
            
        }

        if(!headerPrinted) {
            System.out.println(
            "\nbytes , stdev us ,  ave us  ,  min us  ,  50%% us ,  90%% us ,  99%% us , 99.99%%  ,  max us  ,  samples\n" +
            "------,----------,----------,----------,----------,----------,----------,----------,----------,---------");
            headerPrinted = true;
        }
        System.out.format("%6d,%10.1f,%10.1f,%10.1f,%10.1f,%10.1f,%10.1f,%10.1f,%10.1f,%6d\n",
                        length,
                        deviation,
                        average,
                        min_sample,
                        percentile_50_sample,
                        percentile_90_sample,
                        percentile_99_sample,
                        percentile_9999_sample,
                        max_sample,
                        sampleSize);
    }

    @PostConstruct
    public void go() {
        String role = Parameters.INSTANCE.getProperty("com.a2i.benchmark.role", "local");

        if(role.equals("producer")) {
            LOG.info("Running as a producer");
            speaker.addListener("HelloWorldProducer", new MessageListener<LatencyMessage>() {
                long lat = 0;

                @Override
                public void receive(LatencyMessage message) {
                    try {
                        seeded = true;
                        speaker.send("HelloWorldConsumer", message);
//                        if(running) {
//
//                            ++messageCount;
//
//                            if(messageCount > sampleSize) {
//                                messageCount = 0;
//
//                                ++currentMessageIndex;
//                                if(currentMessageIndex == messages.size()) {
//                                    System.exit(0);
//                                } else {
//                                    currentMessage = messages.get(currentMessageIndex);
//                                }
//                            }
//
//                            currentMessage.sendTime = System.nanoTime();
//                            speaker.send("HelloWorldConsumer", currentMessage);
//                        }
                    } catch (Exception ex) {
                        LOG.error("Error", ex);
                    }
                }
            });
            injectThread.start();
        } else if(role.equals("consumer")) {
            LOG.info("Running as a consumer");
            speaker.addListener("HelloWorldConsumer", new MessageListener<LatencyMessage>() {
                long lat = 0;

                @Override
                public void receive(LatencyMessage message) {

                    lat = System.nanoTime() - message.sendTime - clockOverhead;
                    if(messageCount > throwAway) {

                        // Some weird bug that gives us a bogus latency
                        // It's too big to be reasonable, so throw it out.
                        if(lat < 1e15) {
                            latencies.add(lat);
                        } else {
                            --messageCount;
                        }

//                        if(lat > 5000 * 1000) {
//                            System.out.println("Slow " + messageCount + " - " + (lat / 1000));
//                        }
                    }

                    ++messageCount;
                    
                    if(messageCount > sampleSize + throwAway) {
                        printStats();
                        messageCount = 0;
                        latencies.clear();

                        ++currentMessageIndex;
                        if(currentMessageIndex == messages.size()) {
                            System.exit(0);
                        } else {
                            currentMessage = messages.get(currentMessageIndex);
                        }
                    }

                    try {
                        if(running) {
                            currentMessage.sendTime = System.nanoTime();
                            speaker.send("HelloWorldProducer", currentMessage);
                        }
                    } catch (Exception ex) {
                        LOG.error("Error", ex);
                    }
                }
            });
        }
    }

    public static void main(String[] args) {
        new Latency().go();
    }
}
