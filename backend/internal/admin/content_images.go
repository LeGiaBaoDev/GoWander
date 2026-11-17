package admin

import (
	"context"

	"github.com/jackc/pgx/v5"
)

// SaveContentImage records a photo uploaded for a story body (ADR-041 §4). The file itself was already stored through
// the same pipeline as every other photo (media.Process, EXIF stripped, 480/1600 JPEG, ADR-035).
func (s *PG) SaveContentImage(ctx context.Context, actorID, key string, in PhotoInput, requestID string) error {
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `INSERT INTO content_images (key, alt, credit, license, created_by) VALUES ($1, $2, $3, $4, $5)`,
			key, in.Alt, in.Credit, in.License, actorID); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "content.image", EntityType: "story_image", RequestID: requestID,
			Metadata: map[string]any{"key": key}})
	})
}

// ContentImageExists tells whether a block may point at this key: the Admin can only embed photos it uploaded.
func (s *PG) ContentImageExists(ctx context.Context, key string) (bool, error) {
	var ok bool
	err := s.Pool.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM content_images WHERE key = $1)`, key).Scan(&ok)
	return ok, err
}
