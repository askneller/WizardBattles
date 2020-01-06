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
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.math.Region3i;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector2i;
import org.terasology.math.geom.Vector3i;
import org.terasology.structureTemplates.components.SpawnBlockRegionsComponent;
import org.terasology.utilities.Assets;
import org.terasology.world.block.Block;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.facets.SeaLevelFacet;
import org.terasology.world.generation.facets.SurfaceHeightFacet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WizardTowerLocationFinder {

    private static final Logger logger = LoggerFactory.getLogger(WizardTowerLocationFinder.class);

    GeneratingRegion generatingRegion;
    StructureGenerator structureGenerator = (blockManager, view, rand, posX, posY, posZ) -> {
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
    boolean print;

    public WizardTowerLocationFinder(GeneratingRegion region) {
        this.generatingRegion = region;
    }

    public void populateFacet(WizardTowerFacet facet) {

        // We want to find places with "peak" like properties (a flat area with the land sloping away in most
        // directions). Also we'll find flat areas as large as possible and as high up as possible, even if the sides
        // slope upward. We also want to restrict ourselves to Plains, Mountains, or Snow biomes.
        SurfaceHeightFacet surface = generatingRegion.getRegionFacet(SurfaceHeightFacet.class);
        BiomeFacet biome = generatingRegion.getRegionFacet(BiomeFacet.class);

        float minY = generatingRegion.getRegion().minY();

        SeaLevelFacet seaLevelFacet = generatingRegion.getRegionFacet(SeaLevelFacet.class);

        if (minY > seaLevelFacet.getSeaLevel()) {
            int maxX;
            int maxZ;
            // Is it the correct biome
            boolean biomeCheck = isCorrectBiome(generatingRegion.getRegion(), biome);
            if (biomeCheck) {

                int startX = 0;
                int startZ = 0;
                Region3i region3i = generatingRegion.getRegion();
                maxX = region3i.sizeX();
                maxZ = region3i.sizeZ();
                // Construct a grid indicating whether the land rises or falls
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

                List<Candidate> regionCandidates = new ArrayList<>();
                for (int x = startX; x < maxX; ++x) {
                    for (int z = startZ; z < maxZ; ++z) {
                        Node node = grid.get(x, z);
                        if (node.levelAround()) {
                            int flatAround = findFlatAround(node, grid);
                            boolean suitable = hasPeakLikeProperties(x, z, node, grid);
                            boolean encompasses = generatingRegion.getRegion().encompasses(node.worldX, node.y, node.worldZ);
                            if (encompasses) {
                                if (suitable) {
                                    regionCandidates.add(new Candidate(new Vector2i(node.worldX, node.worldZ), flatAround, node.y));
                                } else if (flatAround > 2 && node.y > 150) {
                                    logger.info("Flat and high around {} node {}", flatAround, node);
                                    regionCandidates.add(new Candidate(new Vector2i(node.worldX, node.worldZ), flatAround, node.y));
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
                        facet.setWorld(first.location.x, first.height, first.location.y, structureGenerator);
                        logger.info("###############################");
                    } else {
                        logger.info("Only candidate loc {} h {} fa {}\n###############################",
                                first.location, first.height, first.flatAround);
                        facet.setWorld(first.location.x, first.height, first.location.y, structureGenerator);
                    }
                }
            }
        }

        if (facet.getRelativeEntries().size() > 0) {
            logger.info("Region {} has {} towers", generatingRegion.getRegion(), facet.getWorldEntries().size());
            logger.info("At world {}, relative {}",
                    facet.getWorldEntries().keySet().stream().findFirst().get(),
                    facet.getRelativeEntries().keySet().stream().findFirst().get());
        }
        generatingRegion.setRegionFacet(WizardTowerFacet.class, facet);
    }

    // Starting at a distance of one around the node in question, check if all blocks around have the same surface height,
    // If so continue expanding the area to check by one at a time.
    private int findFlatAround(Node node, Grid grid) {
        int y = node.y;
        int m = 1;
        boolean flat = true;
        do {
            // check along the 'top' and 'bottom' sides
            topBottom:
            for (int j = -m; j <= m; j += 2 * m) {
                for (int i = -m; i <= m; ++i) {
                    int nX = node.x + i;
                    int nZ = node.z + j;
                    if (nX > -1 && nX < grid.sizeX && nZ > -1 && nZ < grid.sizeZ) {
                        Node toCheck = grid.get(nX, nZ);
                        if (toCheck.y != node.y) {
                            flat = false;
                            break topBottom;
                        }
                    }
                }
            }
            // check along the 'left' and 'right' sides
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

    private boolean hasPeakLikeProperties(int x, int z, Node node, Grid grid) {
        print = false;
        boolean allMatchTwo = allDirectionsAtMarginLessThanAngle(x, z, node, grid, 2, 1.0, 0);
        boolean allMatchSix = allDirectionsAtMarginLessThanAngle(x, z, node, grid, 5, -20.0, 2);

        if (allMatchTwo && allMatchSix) {
            print = true;
            allDirectionsAtMarginLessThanAngle(x, z, node, grid, 5, -20.0, 3);
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

            return greaterThanAngle.size() <= maxAboveAngle;
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
        int opposite = other.y - main.y;
        if (adjacent > 0) {
            double angle = Math.atan(opposite / adjacent);
            return Math.toDegrees(angle);
        }
        return null;
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

    public static enum Direction {
        UP, DOWN, LEVEL, EDGE
    }

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

        @Override
        public String toString() {
            String format = "[x %d (%d), y %d (%f), z %d (%d)]";
            return String.format(format, worldX, x, y, origY, worldZ, z);
        }

    }

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

    }
}
