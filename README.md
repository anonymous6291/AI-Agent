# 🤖 AI Agent (Local LLM Powered)

A secure AI-powered agent that runs locally using **llama.cpp** and executes system-level commands based on natural language input.

🔗 Repository: https://github.com/anonymous6291/AI-Agent.git

---

## 🚀 Features

- 🧠 **Local LLM Integration**
    - Runs fully offline using `llama.cpp`
    - No external API dependency

- 💬 **Natural Language Command Execution**
    - Converts user instructions like:
        - "Send me file xyz"
        - "Check if file exists"
    - Into executable system commands

- 🔐 **Authentication System**
    - Password-based user authentication
    - Prevents unauthorized access

- 🚫 **Security Controls**
    - Blocks users after multiple failed password attempts
    - Protects against brute-force login attempts

- 📁 **File Handling**
    - Can check file existence
    - Send files as output to users

- ⚡ **Automation**
    - Breaks tasks into smaller actions
    - Executes sequentially and responds with results

---

## 🖥️ Supported OS

* Windows
* Linux / Unix
* Mac

---

## 🏗️ Architecture Overview

```
User Input
   ↓
Authentication Layer (Password Check)
   ↓
LLM (llama.cpp)
   ↓
Command Parser
   ↓
System Execution Layer
   ↓
Response / File Output
```

---

## 🛠️ Tech Stack

- **LLM Runtime:** llama.cpp
- **Language:** Java
- **Execution Environment:** Local System
- **Security:** Custom authentication & blocking mechanism

---

## 📦 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/anonymous6291/AI-Agent.git
cd AI-Agent
```

### 2. Setup llama.cpp

```bash
git clone https://github.com/ggerganov/llama.cpp
cd llama.cpp
make
```

Download a compatible GGUF model and place it in the appropriate directory.

---

### 3. Configure the Agent via configuration.json

- Set your password/auth configuration.
- Configure model path, etc.

### 4. Setting up Telegram bot token
- Make file bot_token.txt and store Telegram bot token inside it.

---

## 📖 Usage

### 1. Run the Agent
```bash
java -jar ./target/AI_Agent-1.0-SNAPSHOT.jar
```

### 2. Connect to the Telegram bot and enter the password.

### 3. Your bot is now ready to accept commands.
#### Example
```
User: Send me file test.txt
Agent: [Finds file → Sends output]

User: Check if hello.java exists
Agent: [Checks system → Returns result]
```

---

## 🔐 Security Features

- Password-protected access
- Limited login attempts
- Automatic user blocking
- Controlled command execution

> ⚠️ Note: Be cautious when allowing system-level command execution. Restrict sensitive operations.

---

## ⚙️ Future Improvements

- Role-based access control
- Sandboxed execution
- GUI / Web interface
- Logging & monitoring
- Multi-user session management

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repo
2. Create a new branch
3. Commit changes
4. Open a pull request

---

## 📜 License

MIT License

---

## 💡 Inspiration

This project demonstrates how local LLMs can be used to build **secure, offline AI agents** capable of real system interaction.

---

## ⭐ Support

If you like this project, give it a ⭐ on GitHub!