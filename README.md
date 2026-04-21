# C2 Project Infrastructure
A stable, Java-based Command and Control (C2) framework built on raw Sockets and Maven.
Supports concurrent encrypted connections, SQL logging, and is natively scalable.

## Directory Structure

C2Exercise/
│
├─ src/main/java/
│   ├─ Client.java
│   ├─ ClientHandler.java
│   ├─ CryptoUtils.java
│   ├─ DatabaseManage.java
│   └─ Server.java 
├─ pom.xml
└─ c2_project.db (Generated after first run)


### Features
- **Scalable Concurrency**: Relies on a robust `FixedThreadPool` pattern to gracefully process and queue concurrent massive connection drops without resource starvation.
- **End-to-End Encryption**: Integrates custom bitwise XOR-ciphers and Base64 wrapping on all payloads, strictly prohibiting MITM snooping while remaining highly lightweight. 
- **Admin CLI**: Manage servers on-the-spot without restarting sessions using non-daemon administration handling.
- **Commands**:
  - `status`: Views the number of current active connections.
  - `kill <clientId|all>`: Gracefully disconnects a specific client remotely by ID or sends a termination order to everybody.
  - `echo <clientId|all> <msg>`: Pushes an instantly encrypted secure payload payload across the network to specified clients.
  - `exit`: Securely signals global shutdown sequence preventing memory leaks. 

#### Prerequisites
Java 17 or higher.
Apache Maven installed.

##### Initialization & Setup

Clone this repository and compile the source Java files into the binaries output directory manually.

# 1. Clone the repository
git clone https://github.com/mayaharach-lgtm/C2-Exercise.git
cd "C2-Exercise"

# 2. Compile and download dependencies (SQLite JDBC)
mvn clean compile


### Running the Project

Run these commands entirely in detached terminal instances. Do not run the Client before the Server socket is actively listening.

#### Booting the Server
```bash
# 1. Start the C2 Admin instance. It will open `C2>` prompt for interactions
mvn exec:java "-Dexec.mainClass=Server"
```

#### Launching Clients
```bash
# 2. Boot a generic client listener process
mvn exec:java "-Dexec.mainClass=Client"

# 3. Run commmands 
- `status`: Views the number of current active connections.
- `kill <clientId|all>`: Gracefully disconnects a specific client remotely by ID or sends a termination order to everybody.
-  'run <clientId|all> <command>': Runs bash command on a specific client remotely by ID or sends a  command to everybody.
- `exit`: Securely signals global shutdown sequence preventing memory leaks. 
