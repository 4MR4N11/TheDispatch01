# Simple Node.js container for Angular development

FROM node:20-slim
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies with increased memory
RUN npm install --max-old-space-size=4096

# Copy source code
COPY . .

# Expose port
EXPOSE 4200

# Start Angular dev server with increased memory
CMD ["node", "--max-old-space-size=4096", "node_modules/@angular/cli/bin/ng", "serve", "--host", "0.0.0.0", "--poll", "2000"]
