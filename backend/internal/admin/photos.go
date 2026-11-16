package admin

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
)

// Licences an editor can pick for a photo (ADR-035: every photo keeps its credit and licence, shown in the app).
var PhotoLicenses = []string{"Ảnh của GoWander", "Chủ địa điểm cho phép", "CC BY 4.0", "CC BY-SA 4.0", "CC0"}

// Photo is one photo of a place.
type Photo struct {
	ID, Key, Credit, License, Alt string
	Cover                         bool
}

// PhotoInput is what the upload form sends besides the file.
type PhotoInput struct{ Credit, License, Alt string }

func validatePhoto(in *PhotoInput) error {
	in.Credit, in.Alt = strings.TrimSpace(in.Credit), strings.TrimSpace(in.Alt)
	switch {
	case in.Credit == "" || utf8.RuneCountInString(in.Credit) > 200:
		return bad("ghi công ảnh phải có (ai chụp hoặc nguồn), tối đa 200 ký tự")
	case !slices.Contains(PhotoLicenses, in.License):
		return bad("chọn giấy phép của ảnh")
	case utf8.RuneCountInString(in.Alt) > 300:
		return bad("mô tả ảnh tối đa 300 ký tự")
	}
	return nil
}

func (s *PG) PlacePhotos(ctx context.Context, placeID string) ([]Photo, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id::text, storage_key, source_credit, license_info, coalesce(alt_text, ''), is_cover
		FROM place_media WHERE place_id::text = $1 ORDER BY is_cover DESC, sort_order, created_at`, placeID)
	if err != nil {
		return nil, fmt.Errorf("place photos: %w", err)
	}
	return pgx.CollectRows(rows, pgx.RowToStructByPos[Photo])
}

// AddPlacePhoto records an uploaded photo; a place's first photo becomes its cover.
func (s *PG) AddPlacePhoto(ctx context.Context, actorID, placeID, key string, in PhotoInput, requestID string) error {
	if err := validatePhoto(&in); err != nil {
		return err
	}
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var exists bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM places WHERE id::text = $1)`, placeID).Scan(&exists); err != nil {
			return err
		}
		if !exists {
			return ErrNotFound
		}
		var id string
		err := tx.QueryRow(ctx, `INSERT INTO place_media (place_id, storage_key, media_type, alt_text, source_credit, license_info, sort_order, is_cover)
			SELECT $1::uuid, $2, 'IMAGE', $3, $4, $5, coalesce(max(sort_order), 0) + 1, count(*) = 0 FROM place_media WHERE place_id = $1::uuid
			RETURNING id::text`, placeID, key, orNull(in.Alt), in.Credit, in.License).Scan(&id)
		if err != nil {
			return fmt.Errorf("add photo: %w", err)
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "photo.add", EntityType: "place", EntityID: placeID, RequestID: requestID,
			Metadata: map[string]any{"license": in.License}})
	})
}

// SetPlaceCover makes one photo the place's cover (the app's card and header image).
func (s *PG) SetPlaceCover(ctx context.Context, actorID, placeID, photoID, requestID string) error {
	return pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `UPDATE place_media SET is_cover = false WHERE place_id::text = $1 AND is_cover`, placeID); err != nil {
			return err
		}
		tag, err := tx.Exec(ctx, `UPDATE place_media SET is_cover = true WHERE place_id::text = $1 AND id::text = $2`, placeID, photoID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return ErrNotFound
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "photo.cover", EntityType: "place", EntityID: placeID, RequestID: requestID})
	})
}

// DeletePlacePhoto removes a photo; when it was the cover the next one takes over. It returns the storage key when no
// row uses it any more, so the caller can delete the files.
func (s *PG) DeletePlacePhoto(ctx context.Context, actorID, placeID, photoID, requestID string) (orphan string, err error) {
	err = pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var key string
		var cover bool
		err := tx.QueryRow(ctx, `DELETE FROM place_media WHERE place_id::text = $1 AND id::text = $2 RETURNING storage_key, is_cover`, placeID, photoID).Scan(&key, &cover)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if cover {
			if _, err := tx.Exec(ctx, `UPDATE place_media SET is_cover = true WHERE id = (SELECT id FROM place_media WHERE place_id::text = $1 ORDER BY sort_order, created_at LIMIT 1)`, placeID); err != nil {
				return err
			}
		}
		if orphan, err = unusedKey(ctx, tx, key); err != nil {
			return err
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: "photo.delete", EntityType: "place", EntityID: placeID, RequestID: requestID})
	})
	return orphan, err
}

// unusedKey returns key when no place, dish or story refers to it any more.
func unusedKey(ctx context.Context, tx pgx.Tx, key string) (string, error) {
	if key == "" {
		return "", nil
	}
	var used bool
	err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM place_media WHERE storage_key = $1)
		OR EXISTS (SELECT 1 FROM food_items WHERE image_key = $1) OR EXISTS (SELECT 1 FROM historical_stories WHERE image_key = $1)`, key).Scan(&used)
	if err != nil || used {
		return "", err
	}
	return key, nil
}

// SetGuideImage sets (key != "") or removes (key == "") the photo of a dish or story. It returns the previous key
// when nothing else uses it.
func (s *PG) SetGuideImage(ctx context.Context, actorID, kind, id, key string, in PhotoInput, requestID string) (orphan string, err error) {
	table := map[string]string{"foods": "food_items", "stories": "historical_stories"}[kind]
	if table == "" {
		return "", ErrNotFound
	}
	if key != "" {
		if err := validatePhoto(&in); err != nil {
			return "", err
		}
	}
	err = pgx.BeginFunc(ctx, s.Pool, func(tx pgx.Tx) error {
		var old *string
		err := tx.QueryRow(ctx, `SELECT image_key FROM `+table+` WHERE id::text = $1 FOR UPDATE`, id).Scan(&old)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE `+table+` SET image_key = $2, image_credit = $3, image_license = $4 WHERE id::text = $1`,
			id, orNull(key), orNull(in.Credit), orNull(in.License)); err != nil {
			return fmt.Errorf("set image: %w", err)
		}
		if old != nil && *old != key {
			if orphan, err = unusedKey(ctx, tx, *old); err != nil {
				return err
			}
		}
		action := "photo.add"
		if key == "" {
			action = "photo.delete"
		}
		return audit(ctx, tx, AuditEntry{ActorID: actorID, Action: action, EntityType: GuideKinds[kind].Audit, EntityID: id, RequestID: requestID})
	})
	return orphan, err
}
