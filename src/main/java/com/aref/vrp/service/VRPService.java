package com.aref.vrp.service;


import com.aref.vrp.entity.Customer;
import com.aref.vrp.entity.Vehicle;
import com.aref.vrp.repository.CustomerRepository;
import com.aref.vrp.repository.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class VRPService {

    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;

    public VRPService(CustomerRepository customerRepo,
                        VehicleRepository vehicleRepo) {
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
    }

    public Map<Integer, List<Long>> solveVRP() throws Exception {
        Loader.loadNativeLibraries();

        List<Customer> customers = customerRepo.findAll();
        List<Vehicle> vehicles = vehicleRepo.findAll();

        if (customers.size() == 0 || vehicles.size() == 0) throw new Exception("No data");

        int vehicleCount = vehicles.size();
        int customerCount = customers.size();
        int depotIndex = 0;

        int[] demands = new int[customerCount];
        long[] capacities = new long[vehicleCount];

        for (int i = 0; i < customerCount; i++) demands[i] = customers.get(i).getDemand();
        for (int i = 0; i < vehicleCount; i++) capacities[i] = vehicles.get(i).getCapacity();

        long[][] distanceMatrix = fetchDistanceMatrix(customers);

        RoutingIndexManager manager = new RoutingIndexManager(customerCount, vehicleCount, depotIndex);
        RoutingModel routing = new RoutingModel(manager);

        routing.setArcCostEvaluatorOfAllVehicles(routing.registerTransitCallback((long fromIndex, long toIndex) -> {
            int from = manager.indexToNode(fromIndex);
            int to = manager.indexToNode(toIndex);
            return distanceMatrix[from][to];
        }));

        final int demandCallbackIndex = routing.registerUnaryTransitCallback(fromIndex -> {
            int from = manager.indexToNode(fromIndex);
            return demands[from];
        });

        routing.addDimensionWithVehicleCapacity(
                demandCallbackIndex,
                0L, // null capacity slack
                capacities, // vehicle capacities
                true,
                "Capacity");

        RoutingSearchParameters parameters = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .build();

        Assignment solution = routing.solveWithParameters(parameters);
        if (solution == null) throw new Exception("No solution found");

        Map<Integer, List<Long>> result = new HashMap<>();
        for (int vehicleId = 0; vehicleId < vehicleCount; vehicleId++) {
            List<Long> route = new ArrayList<>();
            long index = routing.start(vehicleId);
            while (!routing.isEnd(index)) {
                int node = manager.indexToNode(index);
                route.add(customers.get(node).getId());
                index = solution.value(routing.nextVar(index));
            }
            result.put(vehicleId, route);
        }

        return result;
    }

    private long[][] fetchDistanceMatrix(List<Customer> customers) throws Exception {
        int size = customers.size();
        long[][] matrix = new long[size][size];
        ObjectMapper mapper = new ObjectMapper();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) matrix[i][j] = 0;
                else {
                    String url = String.format(
                            "http://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                            customers.get(i).getLongitude(), customers.get(i).getLatitude(),
                            customers.get(j).getLongitude(), customers.get(j).getLatitude());

                    String json = WebClient.create().get().uri(url).retrieve().bodyToMono(String.class).block();
                    JsonNode node = mapper.readTree(json);
                    double distance = node.get("routes").get(0).get("distance").asDouble(); // in meters
                    matrix[i][j] = (long) distance;
                }
            }
        }

        return matrix;
    }
}
