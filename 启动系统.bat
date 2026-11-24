@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title ETF momentum backtest system

echo ==================================================
echo   ETF 动量轮动:因子/策略/回测系统 启动中
echo   课题 T03.01 相对动量轮动 · 课程作业 C
echo ==================================================
echo.

rem ---------- 0. 定位 JDK 17(优先项目内置) ----------
set "JAVA_HOME_SET="
for /d %%D in ("%~dp0.jdk\jdk-17*") do (
  if exist "%%D\bin\java.exe" (
    set "JAVA_HOME=%%D"
    set "PATH=%%D\bin;!PATH!"
    set "JAVA_HOME_SET=1"
  )
)
if not defined JAVA_HOME_SET (
  where java >nul 2>nul
  if errorlevel 1 (
    echo [错误] 未找到 java 命令。三种方式任选:
    echo        1. 用 IDEA 打开本项目:File - Project Structure - SDK - Download JDK 17
    echo        2. 命令行执行: winget install --id EclipseAdoptium.Temurin.17.JDK
    echo        3. 手动下载 JDK17 解压到本项目 .jdk 目录
    echo        完成后重新双击本脚本。
    pause
    exit /b 1
  )
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%~v"
echo 当前 Java: !JVER!
echo.

rem ---------- 1. 定位 Maven(优先项目内置,避免系统 Maven 绑定 JDK8) ----------
set "MVN_CMD="
if exist ".maven\apache-maven-3.9.16\bin\mvn.cmd" set "MVN_CMD=.maven\apache-maven-3.9.16\bin\mvn.cmd"
if not defined MVN_CMD (
  where mvn >nul 2>nul && set "MVN_CMD=mvn"
)
if not defined MVN_CMD (
  echo 未检测到 Maven,开始自动下载,约 9 MB,清华镜像...
  if not exist ".maven" mkdir .maven
  powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri 'https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip' -OutFile '.maven\maven.zip' } catch {}"
  if exist ".maven\maven.zip" (
    echo 解压 Maven...
    powershell -NoProfile -Command "Expand-Archive -Path '.maven\maven.zip' -DestinationPath '.maven' -Force"
    del ".maven\maven.zip" >nul 2>nul
    if exist ".maven\apache-maven-3.9.16\bin\mvn.cmd" set "MVN_CMD=.maven\apache-maven-3.9.16\bin\mvn.cmd"
  )
)
if not defined MVN_CMD (
  echo [错误] Maven 自动下载失败。请手动安装 Maven,或用 IDEA 直接打开本项目运行。
  pause
  exit /b 1
)
echo 使用 Maven: !MVN_CMD!
echo.

rem ---------- 2. 编译打包 ----------
echo 正在构建项目,首次约 2-5 分钟,之后很快...
call !MVN_CMD! -s maven-settings.xml -q package -DskipTests
if errorlevel 1 (
  echo.
  echo [错误] 构建失败。常见原因:上次的系统还没关闭,8080 端口被占用或 jar 被锁。
  echo        请关闭所有黑色命令行窗口后重试;仍失败则把上方报错截图反馈。
  pause
  exit /b 1
)
if not exist "target\etf-momentum-system-1.0.0.jar" (
  echo [错误] 未找到构建产物 jar,请截图上方信息反馈。
  pause
  exit /b 1
)

rem ---------- 3. 直接 java -jar 启动(不依赖 Maven 运行环境) ----------
echo.
echo 构建成功,启动应用... 浏览器将自动打开 http://localhost:8080
echo 提示:若页面暂时打不开,等几秒刷新即可;关闭本窗口即停止系统
start "" cmd /c "timeout /t 10 >nul & start http://localhost:8080"
"!JAVA_HOME!\bin\java.exe" -jar target\etf-momentum-system-1.0.0.jar
echo.
echo 应用已停止。
pause
