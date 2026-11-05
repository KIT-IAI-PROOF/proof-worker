#!/bin/bash

# Absolute Paths
DIR_WORKER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIR_SIM_CORE="$DIR_WORKER/../proof-sim-core"
DIR_UTILS="$DIR_WORKER/../proof-utils"

# Fix Paths; paths dependent of sim-core project and Dockerfile setup
SIM_WHEEL_DIR="$DIR_SIM_CORE/dist"
WORKER_WHEEL_DIR="$DIR_WORKER/proof-sim-core/dist/"

# Extract the locally jib-built image tag (proof-worker:<version>) from pom-local.xml.
WORKER_VERSION=$(grep -A1 '<artifactId>proof-worker</artifactId>' "$DIR_WORKER/pom-local.xml" \
    | grep '<version>' | sed -E 's/.*<version>([^<]+)<.*/\1/' | head -1 || true)

# Parse command line arguments
BUILD_WORKER=false
BUILD_SIM_CORE=false
BUILD_UTILS=false
IMAGE_NAME="proof-worker-python:$WORKER_VERSION-local"
DOCKER_FILE="Dockerfile-worker-python"

USE_LOCAL_WORKER=true       # false only when -r (remote base) is used
BASE_IMAGE=""               # optional image (remote or local)

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
        --base-image|-b)
            USE_LOCAL_WORKER=true
            if [[ $# -gt 1 && "$2" != -* ]]; then
                BASE_IMAGE="$2"
                shift 2
            else
                shift
            fi
            ;;
        --use-remote|-r)
            USE_LOCAL_WORKER=false
            if [[ $# -gt 1 && "$2" != -* ]]; then
                BASE_IMAGE="$2"
                shift 2
            else
                shift
            fi
            ;;
        *)
            echo "Unknown option: $1"
            echo -e "Usage: $0 [--worker|-w] [--sim-core|-s] [--utils|-u] [--image|-i IMAGE_NAME] [--base-image|-b [IMG]] [--use-remote|-r [IMG]]"
            echo -e "\nOptions:"
            echo -e "  --worker, -w             Build the (local) proof-worker base image from source"
            echo -e "  --sim-core, -s           Build image based on proof-sim-core Python package changes"
            echo -e "  --utils, -u              Build and install proof-utils before building proof-worker"
            echo -e "  --image, -i NAME         Set final image name and tag (default: proof-worker-python:<version>-local)"
            echo -e "  --base-image, -b [IMG]   Use LOCAL base image <IMG> (default: proof-worker:<version> from pom-local.xml)"
            echo -e "  --use-remote, -r [IMG]   Use REMOTE base image ghcr.io/kit-iai-proof/<IMG> (default: proof-worker:latest)"
            exit 1
            ;;
    esac
done

# Check that at least one building option is chosen
if [ "$BUILD_WORKER" = false ] && [ "$BUILD_SIM_CORE" = false ]; then
    echo "ERROR: Either --worker/-w or --sim-core/-s needs to be provided!"
    exit 2
fi

# A remote base image (-r) is only meaningful together with --sim-core,
# never together with --worker (which builds the local worker).
if [ "$USE_LOCAL_WORKER" = false ] && [ "$BUILD_WORKER" = true ]; then
    echo "ERROR: --use-remote/-r can only be used with --sim-core/-s, not with --worker/-w!"
    echo "Building the worker locally (--worker/-w) while using a remote base makes no sense."
    exit 6
fi

# Check if *.wheel in proof-sim-core/dist exists, else enable building proof-sim-core to build the wheels 
if [ ! -d "$WORKER_WHEEL_DIR" ]; then
  echo -e "\n== No python wheel for proof-sim-core exists in worker directory ($WORKER_WHEEL_DIR does not exist). Building py wheels for proof-sim-core..."
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
    rm -rf "$WORKER_WHEEL_DIR"
    mkdir -p "$WORKER_WHEEL_DIR"

    # Copy only the wheel matching the version from setup.cfg
    echo -e "\n===== Copying wheel for version $SIM_CORE_VERSION_WHEEL from $SIM_WHEEL_DIR to wheels directory...\n"

    # Find and copy the matching wheel file
    WHEEL_PATTERN="proof_sim_core-${SIM_CORE_VERSION_WHEEL}-py3-none-any.whl"
    WHEEL_COPIED=false
    if cp "$SIM_WHEEL_DIR/$WHEEL_PATTERN" "$WORKER_WHEEL_DIR" 2>/dev/null; then
        echo -e "\n===== Successfully copied $WHEEL_PATTERN to $WORKER_WHEEL_DIR\n"
        WHEEL_COPIED=true
    else
        echo "WARNING: Exact wheel pattern not found. Searching for matching wheel..."
        # Try to find any wheel with the version (in case of different platform tags)
        for whl in "$SIM_WHEEL_DIR"/proof_sim_core-"${SIM_CORE_VERSION_WHEEL}"*.whl; do
            if [ -f "$whl" ]; then
                cp "$whl" "$WORKER_WHEEL_DIR"
                echo -e "\n===== Copied $(basename "$whl") to $WORKER_WHEEL_DIR\n"
                WHEEL_COPIED=true
            fi
        done
    fi

    # Verify that at least one wheel was copied
    if [ "$WHEEL_COPIED" = false ] || [ ! "$(ls -A "$WORKER_WHEEL_DIR" 2>/dev/null)" ]; then
        echo "ERROR: No wheel files were copied to $WORKER_WHEEL_DIR"
        echo "Available wheels in $SIM_WHEEL_DIR:"
        ls -la "$SIM_WHEEL_DIR"*.whl 2>/dev/null || echo "No .whl files found"
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

# Resolve the base worker image injected as the Dockerfile's WORKER_IMAGE arg.
BASE_IMG_PATH=""
if [ "$USE_LOCAL_WORKER" = "false" ]; then
    BASE_IMG_PATH="ghcr.io/kit-iai-proof"
    : "${BASE_IMAGE:=proof-worker:latest}"
else
    : "${BASE_IMAGE:=proof-worker:${WORKER_VERSION}}"
fi
WORKER_BASE_IMAGE="$BASE_IMG_PATH/$BASE_IMAGE"

echo -e "\n=== Using base image: $WORKER_BASE_IMAGE"

# Always execute docker build command
echo -e "\n===== Building proof-worker-python image: $IMAGE_NAME from base image: $WORKER_BASE_IMAGE"
docker build -t "$IMAGE_NAME" --build-arg WORKER_IMAGE="$WORKER_BASE_IMAGE" -f docker/$DOCKER_FILE .