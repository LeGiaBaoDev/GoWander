// Command admin manages Admin accounts from the server (ADR-039). Run it where the API's .env is loaded:
//
//	go run ./cmd/admin create-account -login baole -email you@example.com [-name "Tên"] [-role SYSTEM_ADMIN]
//	go run ./cmd/admin reset-password -login baole
//
// Both print a temporary password once; it must be changed at the first sign-in. Nothing else is printed.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/LeGiaBaoDev/GoWander/backend/internal/admin"
	"github.com/LeGiaBaoDev/GoWander/backend/internal/platform/database"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "lỗi:", strings.TrimPrefix(err.Error(), admin.ErrBadInput.Error()+": "))
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		return errors.New("dùng: admin create-account -login … -email … | admin reset-password -login …")
	}
	fs := flag.NewFlagSet(args[0], flag.ContinueOnError)
	login := fs.String("login", "", "mã tài khoản (3–40 ký tự a-z 0-9 . _ -)")
	email := fs.String("email", "", "email nhận link quên mật khẩu (create-account)")
	name := fs.String("name", "", "tên hiển thị (create-account, không bắt buộc)")
	role := fs.String("role", admin.RoleSystemAdmin, "vai trò (create-account)")
	if err := fs.Parse(args[1:]); err != nil {
		return err
	}
	url := os.Getenv("ADMIN_DATABASE_URL")
	if url == "" {
		return errors.New("ADMIN_DATABASE_URL chưa đặt (nạp ../.env trước)")
	}
	ctx := context.Background()
	pool, err := database.NewPool(ctx, url)
	if err != nil {
		return err
	}
	defer pool.Close()
	store := &admin.PG{Pool: pool}
	var temporary string
	switch args[0] {
	case "create-account":
		_, temporary, err = store.CreateAccount(ctx, *login, *email, *name, *role)
	case "reset-password":
		temporary, err = store.SetTemporaryPassword(ctx, *login)
		if errors.Is(err, admin.ErrNotFound) {
			return fmt.Errorf("không có tài khoản %q", strings.ToLower(strings.TrimSpace(*login)))
		}
	default:
		return fmt.Errorf("không có lệnh %q", args[0])
	}
	if err != nil {
		return err
	}
	fmt.Printf("Tài khoản: %s\nMật khẩu tạm (chỉ hiện một lần, đổi ngay khi đăng nhập): %s\n", admin.NormalizeLogin(*login), temporary)
	return nil
}
