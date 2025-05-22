### Running the app locally in docker

To build the app locally,
```commandline
docker build -t my-app -f Dockerfile .
```
* Create app-secrets folder in home directory and create db_password.txt and jwt_private_key.txt files there
* Replace app image with tag of the build command (my-app) in docker-compose.yml file
* Now, Run the app, along with postgres and redis
```commandline
docker-compose up -d
```

### Deploying app in ec2

Check ".github/workflows/build-push-deploy-to-ec2.yml" file for the appropriate github actions which
first builds the image and pushes to your dockerhub private repo and ssh into ec2 and deploy
the build image there

Prerequisites for the Github actions to work
1) EC2 should running, and docker compose should be installed in it
```commandline
sudo yum update -y 

sudo yum install docker -y

sudo service docker start 

sudo usermod -a -G docker ec2-user 

sudo reboot
```
connect to ec2 again check if docker is installed
```commandline
docker info
```
download docker-compose
```commandline
sudo curl -L https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose

sudo chmod +x /usr/local/bin/docker-compose

docker-compose version
```
2) need to transfer docker-compose.yml, app-secrets/db_password.txt and app-secrets/jwt_private_ket.txt to ec2 through scp (Secure Copy Protocol)
send docker-compose file
```commandline
scp -i "your-ec2-key.pem" path/to/docker-compose.yml {user}@{host}: ~/docker-compose.yml
```
send app-secrets
```commandline
scp -i "your-ec2-key.pem" path/to/app-secrets/db_password.txt {user}@{host}:~/app-secrets/db_password.txt

scp -i "your-ec2-key.pem" path/to/app-secrets/jwt_private_key.txt {user}@{host}:~/app-secrets/jwt_private_key.txt
```
3) Allow inbound traffic on tcp port on 8081
