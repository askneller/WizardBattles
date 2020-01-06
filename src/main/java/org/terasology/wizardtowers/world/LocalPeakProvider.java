/*
 * Copyright 2019 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.wizardtowers.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.biomesAPI.Biome;
import org.terasology.core.world.CoreBiome;
import org.terasology.core.world.generator.facets.BiomeFacet;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector2i;
import org.terasology.math.geom.Vector3i;
import org.terasology.rendering.nui.properties.Range;
import org.terasology.structureTemplates.components.SpawnBlockRegionsComponent;
import org.terasology.utilities.Assets;
import org.terasology.utilities.procedural.Noise;
import org.terasology.utilities.procedural.WhiteNoise;
import org.terasology.world.block.Block;
import org.terasology.world.generation.ConfigurableFacetProvider;
import org.terasology.world.generation.Facet;
import org.terasology.world.generation.FacetProviderPlugin;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.Produces;
import org.terasology.world.generation.Requires;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;
import org.terasology.world.generator.plugin.RegisterPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Determines where structures can be placed.  Will put structures at the surface.
 */
@RegisterPlugin
@Produces(LocalPeakFacet.class)
@Requires({
        @Facet(value = SeaLevelFacet.class),
        @Facet(value = SurfaceHeightFacet.class),
        @Facet(value = BiomeFacet.class)
})
public class LocalPeakProvider implements ConfigurableFacetProvider, FacetProviderPlugin {

    private static final Logger logger = LoggerFactory.getLogger(LocalPeakProvider.class);

    private Noise densityNoiseGen;
    private Configuration configuration = new Configuration();
    StructureGenerator structureGenerator;
    int n = 0;
    int max = 10;
    boolean print = false;

    public LocalPeakProvider() {
        structureGenerator = (blockManager, view, rand, posX, posY, posZ) -> {
            Optional<Prefab> prefabOptional = Assets.getPrefab("WizardTowers:tower");
            if (prefabOptional.isPresent()) {
                Prefab prefab = prefabOptional.get();
                SpawnBlockRegionsComponent spawnBlockRegions = prefab.getComponent(SpawnBlockRegionsComponent.class);
                if (spawnBlockRegions != null) {
                    Vector3i vector3i = view.chunkToWorldPosition(posX, posY, posZ);
                    logger.debug("Generating at {} (world center), {} {} {} {} (region relative center)",
                            vector3i, view.getRegion(), posX, posY, posZ);
                    for (SpawnBlockRegionsComponent.RegionToFill regionToFill : spawnBlockRegions.regionsToFill) {
                        Block block = regionToFill.blockType;

                        Region3i region = regionToFill.region;
                        // Block positions are specified relative to the centre of the tower for X and Z,
                        // and relative to the bottom for Y
                        for (Vector3i pos : region) {
                            int relX = pos.x + posX;
                            int relY = pos.y + posY;
                            int relZ = pos.z + posZ;
                            view.setBlock(relX, relY, relZ, block);
                        }
                    }
                }
            }
        };
    }

    /**
     * @param configuration the default configuration to use
     */
    public LocalPeakProvider(Configuration configuration) {
        this();
        this.configuration = configuration;
    }

    @Override
    public void setSeed(long seed) {
        densityNoiseGen = new WhiteNoise(seed);
    }

    @Override
    public void process(GeneratingRegion region) {
        // todo: find a patch big enough to fit a tower (5x5), as high as possible, with as many peak-like properties as possible
        SurfaceHeightFacet surface = region.getRegionFacet(SurfaceHeightFacet.class);
        BiomeFacet biome = region.getRegionFacet(BiomeFacet.class);

        LocalPeakFacet facet =
                new LocalPeakFacet(region.getRegion(), region.getBorderForFacet(LocalPeakFacet.class));

        float minY = region.getRegion().minY();
        float maxY = region.getRegion().maxY();

        SeaLevelFacet seaLevelFacet = region.getRegionFacet(SeaLevelFacet.class);

        if (minY > seaLevelFacet.getSeaLevel()) {
                int maxX;
                int maxZ;
                boolean biomeCheck = isCorrectBiome(region.getRegion(), biome);
                if (biomeCheck) {
                    int margin;
                    int startX;
                    int startZ;

                    startX = 0;
                    startZ = 0;
                    Region3i region3i = region.getRegion();
                    maxX = region3i.sizeX();
                    maxZ = region3i.sizeZ();
                    Grid grid = new Grid(maxX, maxZ, region3i);
                    for (int x = startX; x < maxX; ++x) {
                        for (int z = startZ; z < maxZ; ++z) {
                            float height = surface.get(x, z);
                            grid.set(x, z, height);
                            int worldX = region3i.minX() + x;
                            int worldZ = region3i.minZ() + z;
                            Node node = grid.get(x, z);
                            node.worldX = worldX;
                            node.worldZ = worldZ;
                        }
                    }
                    margin = 5;
                    int heightMargin = 3;
                    startX = margin;
                    startZ = margin;
                    List<Candidate> regionCandidates = new ArrayList<>();
                    for (int x = startX; x < maxX - margin; ++x) {
                        for (int z = startZ; z < maxZ - margin; ++z) {
//                            int worldX = region3i.minX() + x;
//                            int worldZ = region3i.minZ() + z;
                            Node node = grid.get(x, z);
//                            if (!region.getRegion().encompasses(node.x, node.y, node.z)) {
//                                continue;
//                            }
//                            node.worldX = worldX;
//                            node.worldZ = worldZ;
                            if (node.levelAround()) {
//                                if (meetsMargins(x, z, node, grid, margin, heightMargin)) {
//                                    Vector2i position = new Vector2i(x, z);
//                                    regionCandidates.put(position, node);
//                                }
//                                if (++n <= max) {
//                                    logger.info("Checking {}", node);
//                                }
                                int flatAround = findFlatAround(node, grid);
                                boolean suitable = characteriseAround(x, z, node, grid);
                                boolean encompasses = region.getRegion().encompasses(node.worldX, node.y, node.worldZ);
                                if (encompasses) {
                                    if (suitable) {
//                                        logger.info("suitable found, encompasses {}", encompasses);
//                                        logger.info("region {}", region.getRegion());
                                        regionCandidates.add(new Candidate(new Vector2i(node.worldX, node.worldZ), flatAround, node.y));
//                                        if (print && n <= max) {
//                                            logger.info("flatAround {} {}", flatAround, node);
//                                        }
                                    } else if (flatAround > 2 && node.y > 150) {
                                        logger.info("Flat and high around {} node {}", flatAround, node);
                                    }
                                }
                            }
                        }
                    }
                    if (regionCandidates.size() > 0) {
                        logger.info("Found {} candidates", regionCandidates.size());
                        List<Candidate> candidateList = regionCandidates.stream()
                                .sorted((a, b) -> b.height - a.height)
                                .collect(Collectors.toList());

                        Candidate first = candidateList.get(0);
                        Candidate last;
                        if (candidateList.size() > 1) {
                            last = candidateList.get(candidateList.size() - 1);
                            logger.info("highest {} {}, lowest {}",
                                    first.location, first.height, last != null ? last.height : null);
                            if (first.height == last.height) {
                                List<Candidate> mostSpace = regionCandidates.stream()
                                        .sorted((a, b) -> b.flatAround - a.flatAround)
                                        .collect(Collectors.toList());
                                first = mostSpace.get(0);
                                last = mostSpace.get(mostSpace.size() - 1);
                                logger.info("mostSpace {} {}, least {}",
                                        first.location, first.flatAround, last != null ? last.flatAround : null);
                            }
                            logger.info("###############################");
                        } else {
                            logger.info("Only candidate loc {} h {} fa {}\n###############################",
                                    first.location, first.height, first.flatAround);
                        }
                    }
//                    List<Map.Entry<Vector2i, Node>> collect = regionCandidates
//                            .entrySet()
//                            .stream()
//                            .sorted((a, b) -> b.getValue().y - a.getValue().y)
//                            .collect(Collectors.toList());
//
//                    if (collect.size() > 0) {
//                        Map.Entry<Vector2i, Node> first = collect.get(0);
//                        Map.Entry<Vector2i, Node> last = null;
//                        if (collect.size() > 1) {
//                            last = collect.get(collect.size() - 1);
//                        }
//                        if (++n <= max) {
//                            logger.info("highest {}, lowest {}", first.getValue(), last != null ? last.getValue() : null);
//                            logger.info("###############################");
//                        }
//                    }
                }
        }

        region.setRegionFacet(LocalPeakFacet.class, facet);
    }

    private int findFlatAround(Node node, Grid grid) {
        int y = node.y;
        int margin = 0;
        int m = 1;
        boolean flat = true;
        do {
            // along the 'top' and 'bottom'
            topBottom:
            for (int j = -m; j <= m; j += 2 * m) {
                for (int i = -m; i <= m; ++i) {
                    int nX = node.x + i;
                    int nZ = node.z + j;
                    if (nX > -1 && nX < grid.sizeX && nZ > -1 && nZ < grid.sizeZ) {
                        Node toCheck = grid.get(nX, nZ);
                        if (toCheck.y != node.y) {
                            flat = false;
//                            if (n <= max) {
//                                logger.info("Failed topBottom {} {} ({}) {}", nX, nZ, toCheck.y, toCheck);
//                            }
                            break topBottom;
                        }
                    }
                }
            }
            // along the 'left' and 'right'
            if (flat) {
                leftRight:
                for (int i = -m; i <= m; i += 2 * m) {
                    for (int j = -m; j <= m; ++j) {
                        int nX = node.x + i;
                        int nZ = node.z + j;
                        if (nX > -1 && nX < grid.sizeX && nZ > -1 && nZ < grid.sizeZ) {
                            Node toCheck = grid.get(nX, nZ);
                            if (toCheck.y != node.y) {
                                flat = false;
//                                if (n <= max) {
//                                    logger.info("Failed leftRight {} {} ({}) {}", nX, nZ, toCheck.y, toCheck);
//                                }
                                break leftRight;
                            }
                        }
                    }
                }
            }
            if (flat) {
                ++m;
            }
        } while (flat);
        return m - 1;
    }

    private boolean characteriseAround(int x, int z, Node node, Grid grid) {
        print = false;
        boolean allMatchTwo = allDirectionsAtMarginLessThanAngle(x, z, node, grid, 2, 1.0, 0);
        boolean allMatchFour = allDirectionsAtMarginLessThanAngle(x, z, node, grid, 4, -15.0, 2);
//        if (allMatchTwo && allMatchFour && ++n <= max) {
//            logger.info("allMatch 2 and 4 {}", node);
//            print = true;
//        }
        boolean allMatchSix = allDirectionsAtMarginLessThanAngle(x, z, node, grid, 5, -20.0, 2);

        if (allMatchTwo && allMatchSix) {
            print = true;
            allDirectionsAtMarginLessThanAngle(x, z, node, grid, 5, -20.0, 3);
            List<Node> neighboursAtDistance = grid.getNeighboursAtDistance(node, grid, 3);
            boolean allMatch = neighboursAtDistance.stream().allMatch(aNode -> aNode.y >= node.y);
//            logger.info("All flat at 3 distance: {}", allMatch);
            return true;
        }
        return false;
    }

    private boolean allDirectionsAtMarginLessThanAngle(int x, int z, Node node, Grid grid, int margin, double angle, int maxAboveAngle) {
        if (withinMargin(x, margin, grid.sizeX) && withinMargin(z, margin, grid.sizeZ)) {
            List<Node> neighboursAtDistance = grid.getNeighboursAtDistance(node, grid, margin);
            Stream<Double> doubleStream = neighboursAtDistance.stream()
                    .map(neighbour -> getAngleToNode(node, neighbour));

            List<Double> doubles = doubleStream.collect(Collectors.toList());
            List<Double> greaterThanAngle = doubles.stream()
                    .filter(d -> d != null && d > angle).collect(Collectors.toList());

//            if (print && margin > 4) {
//                logger.info("doubles {}", doubles);
//            }
            if (greaterThanAngle.size() <= maxAboveAngle) {
//                if (print) {
//                    logger.info("allMatch {} {} {} {}", margin, angle, maxAboveAngle, node);
//                }
                return true;
            }
        }
        return false;
    }

    private boolean withinMargin(int val, int margin, int size) {
        return val >= margin && val < (size - margin);
    }

    private boolean greaterThan(double angle, double target) {
        return angle > target;
    }

    private Double getAngleToNode(Node main, Node other) {
        int absX = Math.abs(main.x - other.x);
        int absZ = Math.abs(main.z - other.z);
        double adjacent = Math.hypot(absX, absZ);
//        if (++n <= max) {
//            logger.info("getAngleToNode {} {} {}", absX, absZ, adjacent);
//        }
        int opposite = other.y - main.y;
        if (adjacent > 0) {
//            if (n <= max) {
//                logger.info("getAngleToNode \n{} \n{}", main, other);
//            }
            double angle = Math.atan(opposite / adjacent);
//            if (n <= max) {
//                logger.info("opp {} adj {} ({}) - angle {} rad, {} deg",
//                        opposite, adjacent, ((double) opposite / adjacent), angle, Math.toDegrees(angle));
//            }
            return Math.toDegrees(angle);
        }
        return null;
    }

    private boolean meetsMargins(int x, int z, Node node, Grid grid, int horizontalMargin, int verticalMargin) {
        Node lowerLeft = grid.get(x - horizontalMargin, z - horizontalMargin);
        Node left = grid.get(x, z - horizontalMargin);
        Node upperLeft = grid.get(x + horizontalMargin, z - horizontalMargin);
        Node upper = grid.get(x + horizontalMargin, z);
        Node upperRight = grid.get(x + horizontalMargin, z + horizontalMargin);
        Node right = grid.get(x, z + horizontalMargin);
        Node lowerRight = grid.get(x - horizontalMargin, z + horizontalMargin);
        Node lower = grid.get(x - horizontalMargin, z);

        NodeGroup leftGroup = new NodeGroup(CompassDirection.WEST, lowerLeft, left, upperLeft);
        leftGroup.targetHeight = node.y;
        leftGroup.margin = verticalMargin;
        NodeGroup upperGroup = new NodeGroup(CompassDirection.NORTH, upperLeft, upper, upperRight);
        upperGroup.targetHeight = node.y;
        upperGroup.margin = verticalMargin;
        NodeGroup rightGroup = new NodeGroup(CompassDirection.EAST, upperRight, right, lowerRight);
        rightGroup.targetHeight = node.y;
        rightGroup.margin = verticalMargin;
        NodeGroup lowerGroup = new NodeGroup(CompassDirection.SOUTH, lowerRight, lower, lowerLeft);
        lowerGroup.targetHeight = node.y;
        lowerGroup.margin = verticalMargin;
        // We want at MOST one of these groups to be not-lower-than the node in question
        List<NodeGroup> nodeGroupsNotMeetingMargin =
                Stream.of(leftGroup, upperGroup, rightGroup, lowerGroup)
                        // Filter out the groups that are lower than the node in question
                        // I.e. only nodes that are equal or higher will remain
                        .filter(group -> !group.meetsMargin())
                        .collect(Collectors.toList());

        // If more than one group is of grater or equal height, this node is ineligible
        if (nodeGroupsNotMeetingMargin.size() > 1) {
            return false;
        }
        // If only one group is at equal(ish) height then we will consider this eligible
        boolean isEqualHeight = false;
        if (nodeGroupsNotMeetingMargin.size() == 1) {
            NodeGroup group = nodeGroupsNotMeetingMargin.get(0);
            // Does the group meet height of this node

            isEqualHeight = group.meetsHeight();
        }
        return isEqualHeight || nodeGroupsNotMeetingMargin.size() == 0;
    }

    private boolean isCorrectBiome(Region3i region3i, BiomeFacet biomeFacet) {
        int minX = region3i.minX();
        int minZ = region3i.minZ();
        int maxX = region3i.maxX();
        int maxZ = region3i.maxZ();
        Biome biomeMin = biomeFacet.getWorld(minX, minZ);
        Biome biomeMax = biomeFacet.getWorld(maxX, maxZ);
        return (biomeMin.equals(CoreBiome.MOUNTAINS) && biomeMax.equals(CoreBiome.MOUNTAINS))
                || (biomeMin.equals(CoreBiome.SNOW) && biomeMax.equals(CoreBiome.SNOW))
                || (biomeMin.equals(CoreBiome.PLAINS) && biomeMax.equals(CoreBiome.PLAINS));
    }

    @Override
    public String getConfigurationName() {
        return "LocalPeak";
    }

    @Override
    public Component getConfiguration() {
        return configuration;
    }

    @Override
    public void setConfiguration(Component configuration) {
        this.configuration = (Configuration) configuration;
    }

    public static class Candidate {
        Vector2i location;
        int flatAround;
        int height;

        public Candidate(Vector2i location, int flatAround, int height) {
            this.location = location;
            this.flatAround = flatAround;
            this.height = height;
        }
    }

    // TODO use the config probabilities
    public static class Configuration implements Component {
        @Range(min = 0, max = 1.0f, increment = 0.05f, precision = 2, description = "Define the overall structure density")
        public float density = 0.2f;
    }


    // #############################################################################
    // #############################################################################
    public static class NodeGroup {
        List<Node> nodes = new ArrayList<>();
        CompassDirection compassDir;
        int targetHeight = -99999;
        int margin = 3;

        public NodeGroup(CompassDirection direction, Node... nodes) {
            this.compassDir = direction;
            this.nodes.addAll(Arrays.asList(nodes));
        }

        public boolean meetsMargin() {
            if (targetHeight != -99999) {
                return nodes.stream().allMatch(node -> node.y + margin < targetHeight);
            }
            return false;
        }

        public boolean meetsHeight() {
            if (targetHeight != -99999) {
                return nodes.stream().allMatch(node -> node.y <= targetHeight);
            }
            return false;
        }

        @Override
        public String toString() {
            return "NodeGroup{" +
                    "nodes=" + nodes +
                    ", compassDir=" + compassDir +
                    '}';
        }
    }


    // #############################################################################
    // #############################################################################
    public static enum Direction {
        UP, DOWN, LEVEL, EDGE
    }

    // #############################################################################
    // #############################################################################
    public static enum CompassDirection {
        NORTH, SOUTH, EAST, WEST
    }

    // #############################################################################
    // #############################################################################
    public static class Node {

        private float threshold = 0.3f;
        private float upperThreshold = 0.7f;

        public int x;
        public int z;
        public int y; // Y is already world coord
        public int worldX;
        public int worldZ;
        public float origY;
        public Direction north;
        public Direction northEast;
        public Direction east;
        public Direction southEast;
        public Direction south;
        public Direction southWest;
        public Direction west;
        public Direction northWest;
        public Node nodeN;
        public Node nodeNE;
        public Node nodeE;
        public Node nodeSE;
        public Node nodeS;
        public Node nodeSW;
        public Node nodeW;
        public Node nodeNW;

        public Node(int x, int z, float y) {
            this.x = x;
            this.z = z;
            this.origY = y;
            this.y = TeraMath.floorToInt(y);
        }

        /**
         * Is this node "higher" than the other Node
         * @param other
         * @return
         */
        public boolean isHigherThan(Node other) {
            return y - other.y > upperThreshold;
        }

        /**
         * Is this node "lower" than the other Node
         * @param other
         * @return
         */
        public boolean isLowerThan(Node other) {
            return other.y - y > upperThreshold;
        }

        public boolean levelAround() {
            return getAll().stream().allMatch(direction -> direction == Direction.LEVEL);
        }

        public boolean levelOrDownAround() {
            return getAll().stream().allMatch(direction -> direction == Direction.LEVEL || direction == Direction.DOWN);
        }

        public List<Direction> getAll() {
            return Arrays.asList(northWest, north, northEast, east, southEast, south, southWest, west);
        }

        public List<Node> getNeighbours() {
            return Arrays.asList(nodeNW, nodeN, nodeNE, nodeE, nodeSE, nodeS, nodeSW, nodeW);
        }

        public List<NodeDirection> getNodeDirs() {
            return Arrays.asList(
                    new NodeDirection(nodeNW, northWest),
                    new NodeDirection(nodeN, north),
                    new NodeDirection(nodeNE, northEast),
                    new NodeDirection(nodeE, east),
                    new NodeDirection(nodeSE, southEast),
                    new NodeDirection(nodeS, south),
                    new NodeDirection(nodeSW, southWest),
                    new NodeDirection(nodeW, west));
        }

        @Override
        public String toString() {
//            String format = "[x %d, y %d (%f), z %d] \n%6s %6s %6s\n%6s %6s %6s\n%6s %6s %6s";
//            return String.format(format, x, y, origY, z, northWest, north, northEast, west, "", east, southWest, south, southEast);
            String format = "[x %d (%d), y %d (%f), z %d (%d)]";
            return String.format(format, worldX, x, y, origY, worldZ, z);
        }

        public String directionAround() {
            String format = "\n%6s %6s %6s\n%6s %6s %6s\n%6s %6s %6s";
            return String.format(format, northWest, north, northEast, west, "", east, southWest, south, southEast);
        }

        public String heightAround() {
            String format = "\n%6f %6f %6f\n%6f %6f %6f\n%6f %6f %6f";
            return String.format(format, nodeNW.origY, nodeN.origY, nodeNE.origY,
                    nodeW.origY, origY, nodeE.origY, nodeSW.origY, nodeS.origY, nodeSE.origY);
        }
    }

    // #############################################################################
    // #############################################################################
    public static class NodeDirection {
        public Node n;
        public Direction d;

        public NodeDirection(Node node, Direction direction) {
            n = node;
            d = direction;
        }
    }

    // #############################################################################
    // #############################################################################
    public static class Patch {

        private static final Logger logger = LoggerFactory.getLogger(Patch.class);

        public boolean[] included;
        public List<Node> nodes = new ArrayList<>();

        public int sizeX;
        public int sizeZ;

        public boolean upAdjacent;
        public boolean downAdjacent;
        public boolean edgeAdjacent;

        public Patch(int sizeX, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.included = new boolean[sizeX * sizeZ];
        }

        public Patch add(Node node) {
            if (!includes(node)) {
                nodes.add(node);
                included[node.z * sizeZ + node.x] = true;
            }
            return this;
        }

        public boolean includes(Node node) {
            return included[node.z * sizeZ + node.x];
        }

        public void grow() {
            int max = 10;
            int n = 0;
//            logger.info("In grow {}", this);
            List<Node> toAdd = new ArrayList<>();
            do {
                toAdd.clear();
                for (Node node : nodes) {
                    List<NodeDirection> nodeDirs = node.getNodeDirs();
                    for (NodeDirection nodeDirection : nodeDirs) {
                        if (nodeDirection.d == Direction.LEVEL && !includes(nodeDirection.n)) {
                            toAdd.add(nodeDirection.n);
                        }
                        if (nodeDirection.d == Direction.UP) {
                            upAdjacent = true;
                        }
                        if (nodeDirection.d == Direction.DOWN) {
                            downAdjacent = true;
                        }
                        if (nodeDirection.d == Direction.EDGE) {
                            edgeAdjacent = true;
                        }
                    }
                }
                toAdd.forEach(this::add);
            } while (++n <= max && !toAdd.isEmpty());
        }

        public String print() {
            StringBuilder builder = new StringBuilder();
            for (int x = sizeX - 1; x >= 0; --x) {
                builder.append('\n');
                for (int z = 0; z < sizeZ; ++z) {
                    if (included[z * sizeZ + x]) {
                        builder.append('Y');
                    } else {
                        builder.append('N');
                    }
                    builder.append(' ');
                }
            }
            return builder.toString();
        }
    }

    // #############################################################################
    // #############################################################################
    public static class Grid {
        public int sizeX;
        public int sizeZ;

        public boolean print = false;

        private Node[] data;
        private Region3i region;

        public Grid(int sizeX, int sizeZ, Region3i region) {
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            data = new Node[sizeX * sizeZ];
            this.region = region;
        }

        public void set(int x, int z, float y) {
            Node node = new Node(x, z, y);
            set(x, z, node);
        }

        public void set(int x, int z, Node node) {
//            if (print && x < 2 && z < 2) {
//                logger.info("Set {} {}, on {}", x, z, region);
//            }
            if (x == 0) {
                node.southWest = Direction.EDGE;
                node.south = Direction.EDGE;
                node.southEast = Direction.EDGE;
            }
            if (z == 0) {
                node.southWest = Direction.EDGE;
                node.west = Direction.EDGE;
                node.northWest = Direction.EDGE;
            }
            if (x == sizeX - 1) {
                node.northWest = Direction.EDGE;
                node.north = Direction.EDGE;
                node.northEast = Direction.EDGE;
            }
            if (z == sizeZ - 1) {
                node.northEast = Direction.EDGE;
                node.east = Direction.EDGE;
                node.southEast = Direction.EDGE;
            }
            if (x > 0 && z > 0) {
                Node nodeToSouthWest = get(x - 1, z - 1);
                node.nodeSW = nodeToSouthWest;
                nodeToSouthWest.nodeNE = node;
                if (node.isHigherThan(nodeToSouthWest)) {
                    node.southWest = Direction.DOWN;
                    nodeToSouthWest.northEast = Direction.UP;
                } else if (node.isLowerThan(nodeToSouthWest)) {
                    node.southWest = Direction.UP;
                    nodeToSouthWest.northEast = Direction.DOWN;
                } else {
                    node.southWest = Direction.LEVEL;
                    nodeToSouthWest.northEast = Direction.LEVEL;
                }
            }
            if (x > 0) {
                Node nodeToSouth = get(x - 1, z);
                node.nodeS = nodeToSouth;
                nodeToSouth.nodeN = node;
                if (node.isHigherThan(nodeToSouth)) {
                    node.south = Direction.DOWN;
                    nodeToSouth.north = Direction.UP;
                } else if (node.isLowerThan(nodeToSouth)) {
                    node.south = Direction.UP;
                    nodeToSouth.north = Direction.DOWN;
                } else {
                    node.south = Direction.LEVEL;
                    nodeToSouth.north = Direction.LEVEL;
                }
            }
            if (z > 0) {
                Node nodeToWest = get(x, z - 1);
                node.nodeW = nodeToWest;
                nodeToWest.nodeE = node;
                if (node.isHigherThan(nodeToWest)) {
                    node.west = Direction.DOWN;
                    nodeToWest.east = Direction.UP;
                } else if (node.isLowerThan(nodeToWest)) {
                    node.west = Direction.UP;
                    nodeToWest.east = Direction.DOWN;
                } else {
                    node.west = Direction.LEVEL;
                    nodeToWest.east = Direction.LEVEL;
                }
            }
            if (x > 0 && z < sizeZ - 1) {
                Node nodeToSouthEast = get(x - 1, z + 1);
                node.nodeSE = nodeToSouthEast;
                nodeToSouthEast.nodeNW = node;
                if (node.isHigherThan(nodeToSouthEast)) {
                    node.southEast = Direction.DOWN;
                    nodeToSouthEast.northWest = Direction.UP;
                } else if (node.isLowerThan(nodeToSouthEast)) {
                    node.southEast = Direction.UP;
                    nodeToSouthEast.northWest = Direction.DOWN;
                } else {
                    node.southEast = Direction.LEVEL;
                    nodeToSouthEast.northWest = Direction.LEVEL;
                }
            }

            data[z * sizeZ + x] = node;

//            if (print && x < 4 && z < 4) {
//                logger.info("Node {}", node.toString());
//            }
        }

        public Node get(int x, int z) {
            return data[z * sizeZ + x];
        }

        public List<Node> getNeighboursAtDistance(Node node, Grid grid, int distance) {
            List<Node> neighbours = new ArrayList<>();
            for (int deltaX = -distance; deltaX <= distance; deltaX += distance) {
                for (int deltaZ = -distance; deltaZ <= distance; deltaZ += distance) {
                    if (deltaX == 0 && deltaZ == 0) {
                        continue;
                    }
                    neighbours.add(grid.get(node.x + deltaX, node.z + deltaZ));
                }
            }
            return neighbours;
        }

        public Patch patchFrom(Node node) {
            Patch patch = new Patch(sizeX, sizeZ);
            patch.add(node);
            node.getNeighbours().forEach(patch::add);
            return patch;
        }
    }
}
