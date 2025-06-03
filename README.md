### Creating the secret for Docker
To be able to use docker secrets, docker needs to be swarm mode
```commandline
docker swarm init
```
Store necessary environment variables as secrets in docker
```commandline
printf "my super secret" | docker secret create my_secret -
```

### Running the App in Docker
First build the docker image of app
```commandline
docker build -t authservice-app -f Dockerfile .
```
Now, Run the app, along with postgres and redis
```commandline
docker-compose up -d
```
