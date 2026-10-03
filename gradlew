#!/bin/sh
# 指向本机缓存的分发版 gradle（SD 卡挂载点无法直接写 git 对象，用本地 gradle）
GRADLE_BIN="/root/.gradle/wrapper/dists/gradle-8.7-bin/anc4mv9t277pws4qhs0gnw9pj/gradle-8.7/bin/gradle"
exec "$GRADLE_BIN" "$@"
