# iSure 时延分析后端使用说明

## 1. 环境要求

请先安装：

```text
Java 8 或更高版本
Maven 3.x
Python 3
Ubuntu / WSL2
```

检查：

```bash
java -version
mvn -version
python3 --version
```

## 2. 下载后端

```bash
git clone -b feature/llvmta-integration \
  https://github.com/wangludemon/System_Time_Analysis_Server_Side.git \
  iSure-Server

cd iSure-Server
```

## 3. 安装 LLVMTA

LLVMTA 官方仓库：

```text
https://github.com/RTS-SYSU/Timing-Analysis-Multicores
```

请按照 LLVMTA 官方文档完成安装和编译。

推荐目录：

```text
/home/当前用户/llvmta
```

安装完成后至少应存在：

```text
~/llvmta/build/bin/llvmta
~/llvmta/testcases/run.py
```

**当前开发版本默认调用：**

```text
~/llvmta/testcases/runf.py
```

如果你使用官方 `run.py`，启动前执行（重要！）：

```bash
export LLVMTA_SCRIPT=run.py
```

如果 LLVMTA 不在 `~/llvmta`，例如位于 `/opt/isure/llvmta`，启动前执行：

```bash
export LLVMTA_HOME=/opt/isure/llvmta
```

## 4. 配置说明

配置文件：

```text
src/main/resources/application.properties
```

默认配置：

```properties
server.port=8080
llvmta.home=${LLVMTA_HOME:}
llvmta.script=${LLVMTA_SCRIPT:runf.py}
llvmta.python=${LLVMTA_PYTHON:python3}
```

常用配置示例：

```bash
export LLVMTA_HOME=/home/user/llvmta
export LLVMTA_SCRIPT=run.py
export LLVMTA_PYTHON=python3
```

## 5. 编译并启动

```bash
cd ~/iSure-Server
mvn clean compile -DskipTests
mvn spring-boot:run
```

启动成功后应看到：

```text
Tomcat started on port(s): 8080
Started ServeSideApplication
```

## 6. 检查服务

```bash
curl http://127.0.0.1:8080/api/rta/health
```

正常结果中应包含：

```json
{
  "status": "UP",
  "llvmtaAvailable": true
}
```

## 7. 准备 testcase

testcase 目录至少应包含：

```text
CoreInfo.json
LoopAnnotations.csv
LLoopAnnotations.csv
C 源代码
```

本仓库下的示例目录：

```text
examples/llvmta-testcase/test
```

核心数量由 `CoreInfo.json` 自动读取，前端不需要输入核心数量。

## 8. 运行 LLVMTA 并导入 WCET

```bash
curl -X POST http://127.0.0.1:8080/api/rta/llvmta/import -H "Content-Type: application/json" -d '{"testcasePath":"/home/user/iSure-Server/examples/llvmta-testcase/test","frequencyGHz":1.6}'
```

参数说明：

```text
testcasePath：testcase 的 Linux 路径
frequencyGHz：处理器频率，单位 GHz
```

后端会：

```text
运行 LLVMTA
→ 读取 output/WCET.json
→ 将 cycles 转换为微秒
→ 保存为当前任务系统
```

## 9. Windows Qt 连接 WSL 后端

在 Windows PowerShell 中查询 WSL IP：

```powershell
wsl -d Ubuntu-22.04 hostname -I
```

例如：

```text
172.20.179.46
```

Qt 前端填写：

```text
IP：172.20.179.46
```

Windows 测试：

```powershell
curl.exe http://172.20.179.46:8080/api/rta/health
```

注意：WSL IP 在重启后可能变化。
