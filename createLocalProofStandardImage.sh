#!/bin/bash

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
WHEEL_DIR=../proof-sim-core/dist
if [ ! -d "$WHEEL_DIR" ]; then
  echo -e "\n== No python wheel for proof-sim-core exist ($WHEEL_DIR does not exist). Building py wheels for proof-sim-core..."
  BUILD_SIM_CORE=true
fi

# Check if $BUILD_SIM_CORE flag is provided
if [ "$BUILD_SIM_CORE" = true ]; then
    echo -e "\n===== Building Python package...\n"
    
    # Check if proof-sim-core directory exists
    if [ ! -d "../proof-sim-core" ]; then
        echo "ERROR: proof-sim-core directory not found at ../proof-sim-core"
        echo "Expected location: $(cd .. && pwd)/proof-sim-core"
        exit 4
    fi

    cd ../proof-sim-core
    python3 -m build
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to build Python package with error code '$?'"
        exit 5
    fi

    cd ../proof-worker
fi

# Check if $BUILD_WORKER flag is provided
if [ "$BUILD_WORKER" = true ]; then

    # Check if $BUILD_UTILS flag is provided
    if [ "$BUILD_UTILS" = true ]; then
        echo -e "\n===== Installing proof-utils dependency...\n"
        
        # Check if proof-utils directory exists
        if [ ! -d "../proof-utils" ]; then
            echo "ERROR: proof-utils directory not found at ../proof-utils"
            echo "Expected location: $(cd .. && pwd)/proof-utils"
            exit 7
        fi
        
        cd ../proof-utils
        mvn clean install -DskipTests
        
        if [ $? -ne 0 ]; then
            echo "ERROR: Failed to install proof-utils with error code '$?'"
            exit 8
        fi
        
        cd ../proof-worker
    fi

    echo -e "\n===== Building proof-worker image...\n"
        
    mvn clean compile jib:dockerBuild -Dimage=proof-worker:local -f pom-local.xml
    
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

# Always execute docker build commands
echo -e "\n===== Building py-build container...\n"
docker build -t python-build-test -f docker/standard/$DOCKER_FILE --target python-build-test --build-arg BUILDCONTEXT=proof-worker ..
echo -e "\n===== Building proof-standard image: $IMAGE_NAME\n"
docker build -t "$IMAGE_NAME" -f docker/standard/$DOCKER_FILE --target final --build-arg BUILDCONTEXT=proof-worker ..