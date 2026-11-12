// Package migrations embeds the versioned SQL migrations (ADR-028). Files: NNNN_name.up.sql / NNNN_name.down.sql.
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
