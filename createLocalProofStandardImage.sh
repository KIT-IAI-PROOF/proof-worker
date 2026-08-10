#!/bin/bash

# Absolute Paths
DIR_WORKER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIR_SIM_CORE="$DIR_WORKER/../proof-sim-core"
DIR_UTILS="$DIR_WORKER/../proof-utils"

# Parse command line arguments
BUILD_WORKER=false
BUILD_SIM_CORE=false
BUILD_UTILS=false
IMAGE_NAME="proof-worker-python:local"
USE_LOCAL_WORKER=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --worker|-w)
            BUILD_WORKER=true
            shift
            ;;
        --sim-core|-s)
            BUILD_SIM_CORE=true
            shift
            ;;
        --utils|-u)
            BUILD_UTILS=true
            shift
            ;;
        --image|-i)
            IMAGE_NAME="$2"
            shift 2
            ;;
        --use-remote|-r)
            USE_LOCAL_WORKER=false
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo -e "Usage: $0 [--worker|-w] [--sim-core|-s] [--utils|-u] [--image|-i IMAGE_NAME] [--use-remote|-r]"
            echo -e "\nOptions:"
            echo -e "  --worker, -w          Build image based on proof-worker changes"
            echo -e "  --sim-core, -s        Build image based on proof-sim-core Python package changes"
            echo -e "  --utils, -u           Build and install proof-utils before building proof-worker"
            echo -e "  --image, -i NAME      Set final image name and tag (default: proof-worker-python:local)"
            echo -e "  --use-remote, -r      Use remote proof-worker from iai-artifactory instead of locally built on"
            exit 1
            ;;
    esac
done

# Check that at least one building option is chosen
if [ "$BUILD_WORKER" = false ] && [ "$BUILD_SIM_CORE" = false ]; then
    echo "ERROR: Either --worker/-w or --sim-core/-s needs to be provided!"
    exit 2
fi

# Check that --use-remote is only used with --sim-core
if [ "$USE_LOCAL_WORKER" = false ] && [ "$BUILD_WORKER" = true ]; then
    echo "ERROR: --use-remote/-r can only be used with --sim-core/-s, not with --worker/-w!"
    echo "Building the worker locally (--worker/-w) while using a remote worker makes no sense."
    exit 6
fi

# Check if *.wheel in proof-sim-core/dist exists, else enable building proof-sim-core to build the wheels 
WHEEL_DIR="$DIR_SIM_CORE/dist"
if [ ! -d "$WHEEL_DIR" ]; then
  echo -e "\n== No python wheel for proof-sim-core exist ($WHEEL_DIR does not exist). Building py wheels for proof-sim-core..."
  BUILD_SIM_CORE=true
fi

# Check if $BUILD_SIM_CORE flag is provided
if [ "$BUILD_SIM_CORE" = true ]; then
    echo -e "\n===== Building Python package...\n"
    
    # Check if proof-sim-core directory exists
    if [ ! -d "$DIR_SIM_CORE" ]; then
        echo "ERROR: proof-sim-core directory not found at $DIR_SIM_CORE"
        echo "Expected location: $DIR_SIM_CORE"
        exit 4
    fi

    cd "$DIR_SIM_CORE"
    # Enable this to remove old wheel and source distribution files automatically to ensure we only have the newly built ones in the dist directory
    # rm -f dist/*.whl dist/*.tar.gz
    python3 -m build
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to build Python package with error code '$?'"
        exit 5
    fi

    cd "$DIR_WORKER"

    # Get the version from setup.cfg (converts e.g. "2.3.1-dev0" to "2.3.1.dev0" for wheel filename)
    SIM_CORE_VERSION=$(grep "^version" "$DIR_SIM_CORE/setup.cfg" | cut -d'=' -f2 | tr -d ' ')
    # Convert hyphen to dot for PEP 440 wheel filename format (e.g., 2.3.1-dev0 -> 2.3.1.dev0)
    SIM_CORE_VERSION_WHEEL=$(echo "$SIM_CORE_VERSION" | sed 's/-/./g')

    echo -e "\n===== Version from setup.cfg: $SIM_CORE_VERSION (wheel format: $SIM_CORE_VERSION_WHEEL)\n"

    # Empty the wheels directory to ensure only the correct wheel is present
    echo -e "\n===== Clearing wheels directory...\n"
    rm -rf wheels/*
    mkdir -p wheels

    # Copy only the wheel matching the version from setup.cfg
    echo -e "\n===== Copying wheel for version $SIM_CORE_VERSION_WHEEL from $DIR_SIM_CORE/dist to wheels directory...\n"

    # Find and copy the matching wheel file
    WHEEL_PATTERN="proof_sim_core-${SIM_CORE_VERSION_WHEEL}-py3-none-any.whl"
    WHEEL_COPIED=false
    if cp "$DIR_SIM_CORE/dist/$WHEEL_PATTERN" wheels/ 2>/dev/null; then
        echo -e "\n===== Successfully copied $WHEEL_PATTERN to wheels/\n"
        WHEEL_COPIED=true
    else
        echo "WARNING: Exact wheel pattern not found. Searching for matching wheel..."
        # Try to find any wheel with the version (in case of different platform tags)
        for whl in "$DIR_SIM_CORE/dist"/proof_sim_core-"${SIM_CORE_VERSION_WHEEL}"*.whl; do
            if [ -f "$whl" ]; then
                cp "$whl" wheels/
                echo -e "\n===== Copied $(basename "$whl") to wheels/\n"
                WHEEL_COPIED=true
            fi
        done
    fi

    # Verify that at least one wheel was copied
    if [ "$WHEEL_COPIED" = false ] || [ ! "$(ls -A wheels 2>/dev/null)" ]; then
        echo "ERROR: No wheel files were copied to wheels/"
        echo "Available wheels in $DIR_SIM_CORE/dist:"
        ls -la "$DIR_SIM_CORE/dist/"*.whl 2>/dev/null || echo "No .whl files found"
        exit 9
    fi
fi

# Check if $BUILD_WORKER flag is provided
if [ "$BUILD_WORKER" = true ]; then

    # Check if $BUILD_UTILS flag is provided
    if [ "$BUILD_UTILS" = true ]; then
        echo -e "\n===== Installing proof-utils dependency...\n"
        
        # Check if proof-utils directory exists
        if [ ! -d "$DIR_UTILS" ]; then
            echo "ERROR: proof-utils directory not found at $DIR_UTILS"
            echo "Expected location: $DIR_UTILS"
            exit 7
        fi
        
        cd "$DIR_UTILS"
        mvn clean install -DskipTests
        
        if [ $? -ne 0 ]; then
            echo "ERROR: Failed to install proof-utils with error code '$?'"
            exit 8
        fi
        
        cd "$DIR_WORKER"
    fi

    echo -e "\n===== Building proof-worker image...\n"
        
    mvn clean compile jib:dockerBuild@deploy-regular -f pom-local.xml

    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to build proof-worker image with error code '$?'"
        exit 3
    fi
fi

# Determine which worker image to use
if [ "$USE_LOCAL_WORKER" = true ]; then
    echo -e "\n=== Using local proof-worker:local image"
    DOCKER_FILE="Dockerfile-from-local"
else
    echo -e "\n=== Using remote proof-worker:latest from iai-artifactory"
    DOCKER_FILE="Dockerfile-from-remote"
fi

# Always execute docker build command
echo -e "\n===== Building proof-worker-python image: $IMAGE_NAME\n"
docker build -t "$IMAGE_NAME" -f docker/standard/$DOCKER_FILE .