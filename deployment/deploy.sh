#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$DEPLOY_DIR")"

check_env() {
  if [ ! -f "$DEPLOY_DIR/.env" ]; then
    echo "缺少 deployment/.env，请先从 .env.example 复制并填写。"
    exit 1
  fi
}

generate_nginx_config() {
  check_env
  # shellcheck disable=SC1091
  source "$DEPLOY_DIR/.env"
  if [ -z "${DOMAIN:-}" ]; then
    echo "请在 deployment/.env 中设置 DOMAIN"
    exit 1
  fi

  mkdir -p "$DEPLOY_DIR/nginx/generated"
  sed "s/your-domain.com/$DOMAIN/g" \
    "$DEPLOY_DIR/nginx/nightly-pick.conf.template" \
    > "$DEPLOY_DIR/nginx/generated/nightly-pick.conf"

  echo "已生成 Nginx 配置：$DEPLOY_DIR/nginx/generated/nightly-pick.conf"
}

build() {
  check_env
  cd "$DEPLOY_DIR"
  docker compose build
}

up() {
  check_env
  cd "$DEPLOY_DIR"
  docker compose up -d
}

down() {
  cd "$DEPLOY_DIR"
  docker compose down
}

restart() {
  cd "$DEPLOY_DIR"
  if [ "${1:-}" = "--force" ]; then
    docker compose up -d --build --force-recreate
    return
  fi
  docker compose restart
}

logs() {
  cd "$DEPLOY_DIR"
  docker compose logs -f --tail=100 "$@"
}

ps_services() {
  cd "$DEPLOY_DIR"
  docker compose ps
}

pull_repo() {
  cd "$PROJECT_ROOT"
  git pull
}

update() {
  pull_repo
  build
  up
}

case "${1:-help}" in
  nginx)
    generate_nginx_config
    ;;
  build)
    build
    ;;
  up)
    up
    ;;
  down)
    down
    ;;
  restart)
    shift || true
    restart "${1:-}"
    ;;
  logs)
    shift
    logs "$@"
    ;;
  ps)
    ps_services
    ;;
  pull)
    pull_repo
    ;;
  update)
    update
    ;;
  *)
    cat <<EOF
用法: ./deployment/deploy.sh <command>

命令:
  nginx      生成系统 Nginx 配置文件
  build      构建 server / agent 镜像
  up         启动 postgres / agent / server
  down       停止并删除容器
  restart    重启容器
  restart --force  重新构建并强制重建容器（重新加载 .env）
  logs       查看日志，可追加服务名
  ps         查看容器状态
  pull       拉取最新代码
  update     git pull + build + up
EOF
    ;;
esac
