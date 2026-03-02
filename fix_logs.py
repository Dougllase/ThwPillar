import re

# 读取文件
with open('src/main/java/com/newpillar/game/GameManager.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 替换调试日志
# 将 this.plugin.getLogger().info("[调试] ...") 改为 this.debugLogger.debug("...")
content = re.sub(
    r'this\.plugin\.getLogger\(\)\.info\("\[调试\] ',
    'this.debugLogger.debug("',
    content
)

# 写回文件
with open('src/main/java/com/newpillar/game/GameManager.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("GameManager.java 调试日志替换完成")
