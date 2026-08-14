# Khóa mã hóa & ký token — CHỖ NÀY KHÔNG BAO GIỜ ĐƯỢC COMMIT

Thư mục dành cho môi trường **local**. `.gitignore` đã chặn `*.pem` / `*.key`,
nhưng đừng dùng `git add -f`.

Sinh khóa (xem `docs/setup-guideline.md`):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out deploy/keys/jwt-private.pem
openssl rsa -pubout -in deploy/keys/jwt-private.pem -out deploy/keys/jwt-public.pem
openssl rand -base64 32          # dán vào AES_KEY_V1
```

Trên máy chủ Staging/Production khóa nằm ở `/opt/songnhue/keys/`, **ngoài bản
backup DB** (`architecture-review.md` §6.5, §9.3). Khóa lọt vào bản backup thì
mã hóa cột nhạy cảm mất hết ý nghĩa.
