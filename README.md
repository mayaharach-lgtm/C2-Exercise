# C2 Project Infrastructure

A stable, purely Java-based Command and Control (C2) framework built on raw Sockets and ThreadPools.
Supports concurrent encrypted remote connections natively scalable up to user limits.

## Directory Structure

C2Exercise/
│
├─ src/
│   ├─ CryptoUtils.java
│   ├─ Client.java
│   ├─ ClientHandler.java
│   └─ Server.java 


### Features
- **Scalable Concurrency**: Relies on a robust `FixedThreadPool` pattern to gracefully process and queue concurrent massive connection drops without resource starvation.
- **End-to-End Encryption**: Integrates custom bitwise XOR-ciphers and Base64 wrapping on all payloads, strictly prohibiting MITM snooping while remaining highly lightweight. 
- **Admin CLI**: Manage servers on-the-spot without restarting sessions using non-daemon administration handling.
- **Commands**:
  - `status`: Views the number of current active connections.
  - `kill <clientId|all>`: Gracefully disconnects a specific client remotely by ID or sends a termination order to everybody.
  - `echo <clientId|all> <msg>`: Pushes an instantly encrypted secure payload payload across the network to specified clients.
  - `exit`: Securely signals global shutdown sequence preventing memory leaks. 

## Initialization & Setup

Clone this repository and compile the source Java files into the binaries output directory manually.

```bash
# 1. Clone the repository
git clone https://github.com/mayaharach-lgtm/C2-Exercise.git
cd "C2-Exercise"

# 2. Compile Java sources into the binary directory
javac -d bin src/*.java
```

### Running the Project

Run these commands entirely in detached terminal instances. Do not run the Client before the Server socket is actively listening.

#### Booting the Server
```bash
# 1. Start the C2 Admin instance. It will open `C2>` prompt for interactions
java -cp bin Server
```

#### Launching Clients
```bash
# 2. Boot a generic client listener process
java -cp bin Client

# 3. Run commmands 
- `status`: Views the number of current active connections.
- `kill <clientId|all>`: Gracefully disconnects a specific client remotely by ID or sends a termination order to everybody.
- `echo <clientId|all> <msg>`: Pushes an instantly encrypted secure payload payload across the network to specified      clients.
- `exit`: Securely signals global shutdown sequence preventing memory leaks. 
