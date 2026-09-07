/**
 * Có đang ở chế độ màn hình lớn không.
 *
 * <p>Tách thành hàm riêng để chỉ có **một** chỗ biết chuỗi `'wall'`. Rải `mode === 'wall'`
 * ở nhiều component thì một chỗ viết hoa khác đi là một nửa màn hình đổi theme.
 */
export function useWallMode(thamSo: URLSearchParams): boolean {
  return thamSo.get('mode')?.toLowerCase() === 'wall';
}
