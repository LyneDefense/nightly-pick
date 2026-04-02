# Nightly Pick 部署目录

这个目录用于部署 `nightly-pick` monorepo，采用下面的方式：

- `nightly-pick-server`：Docker 容器
- `nightly-pick-agent`：Docker 容器
- `PostgreSQL`：Docker 容器
- `Nginx`：Ubuntu 系统服务，不使用 Docker

## 目录结构

```text
deployment/
  .env.example
  docker-compose.yml
  deploy.sh
  nginx/
    nightly-pick.conf.template
    generated/
```

## 端口约定

- `Nginx`: `80` / `443`
- `nightly-pick-agent`: `127.0.0.1:18080`
- `nightly-pick-server`: `127.0.0.1:18081`
- `PostgreSQL`: `127.0.0.1:15432`

这些端口只有本机可访问，公网只暴露 `80/443`。

## 初始化

1. 复制环境变量模板：

```bash
cd /opt/nightly-pick/repo
cp deployment/.env.example deployment/.env
```

2. 修改 `deployment/.env`

至少要改这些：

```env
DOMAIN=api.your-domain.com
POSTGRES_PASSWORD=你的数据库密码
AUDIO_PUBLIC_BASE_URL=https://你的域名/nightly
TEXT_PROVIDER=mock
SPEECH_PROVIDER=mock
```

如果你要用 MiniMax，再补：

```env
TEXT_PROVIDER=minimax
SPEECH_PROVIDER=minimax
MINIMAX_API_KEY=your_key
MINIMAX_TTS_VOICE_ID="Chinese (Mandarin)_Warm_Bestie"
```

## 启动服务

```bash
cd /opt/nightly-pick/repo
./deployment/deploy.sh build
./deployment/deploy.sh up
./deployment/deploy.sh ps
```

健康检查：

```bash
curl http://127.0.0.1:18080/health
curl http://127.0.0.1:18081/health
```

## 生成系统 Nginx 配置

```bash
cd /opt/nightly-pick/repo
./deployment/deploy.sh nginx
```

生成后的文件在：

```text
deployment/nginx/generated/nightly-pick.conf
```

把它复制到 Ubuntu 系统 nginx：

```bash
sudo cp deployment/nginx/generated/nightly-pick.conf /etc/nginx/sites-available/nightly-pick
sudo ln -sf /etc/nginx/sites-available/nightly-pick /etc/nginx/sites-enabled/nightly-pick
sudo nginx -t
sudo systemctl reload nginx
```

## 证书

建议使用系统 certbot：

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.your-domain.com
```

## 更新代码

```bash
cd /opt/nightly-pick/repo
./deployment/deploy.sh update
```

## 查看日志

查看全部：

```bash
./deployment/deploy.sh logs
```

只看 server：

```bash
./deployment/deploy.sh logs server
```

只看 agent：

```bash
./deployment/deploy.sh logs agent
```
