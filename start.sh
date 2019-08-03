# Usage: ./start.sh /dev/ttyUSB0
docker run -it -v $(pwd):/volume --device $1:/dev/ttyUSB0 btrng:latest /bin/bash
