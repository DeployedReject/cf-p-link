# -----------------------------
# Dockerfile — Arch Linux + JDK + Cloudflared + Wrangler
# -----------------------------
FROM archlinux:latest

# Install system packages
RUN pacman -Sy --noconfirm archlinux-keyring && \
    pacman -Syu --noconfirm --noprogressbar && \
    pacman -S --noconfirm --noprogressbar \
      base-devel git curl ca-certificates unzip sudo zsh \
      nodejs npm jdk-openjdk && \
    pacman -Scc --noconfirm

# Create non-root user
RUN useradd -m -G wheel developer && \
    echo "developer ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/developer

# Install Cloudflare tools
RUN curl -L -o /usr/local/bin/cloudflared \
      "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64" && \
    chmod +x /usr/local/bin/cloudflared && \
    npm install -g wrangler

# App directory
RUN mkdir -p /opt/app && chown -R developer:developer /opt/app
WORKDIR /opt/app

# Copy Java source
COPY ./src ./src

# Compile Java source
RUN mkdir -p out && javac -d out $(find src -name "*.java")

# Environment variables
ENV JAVA_HOME=/usr/lib/jvm/default
ENV PATH="$PATH:/usr/local/bin:$(npm bin -g)"

# Switch to non-root user
USER developer

# Run the Java program by default
CMD ["/usr/lib/jvm/default/bin/java", "-cp", "out", "subprocessMaster"]

