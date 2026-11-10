FROM nginx:alpine

ENV PORT=8080

COPY Digitrans-QR.html /usr/share/nginx/html/index.html
COPY railway/qr-nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 8080
