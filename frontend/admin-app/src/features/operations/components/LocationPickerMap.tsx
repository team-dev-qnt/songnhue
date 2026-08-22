import { useQuery } from '@tanstack/react-query';
import { Empty, Spin } from 'antd';
import L from 'leaflet';
import { useEffect, useRef } from 'react';

import { api } from '@/shared/apiClient';
import { type DashboardView } from '@/shared/api-types';

import 'leaflet/dist/leaflet.css';

interface LocationPickerMapProps {
  latitude?: number;
  longitude?: number;
  onChange?: (lat: number, lng: number) => void;
  height?: number | string;
}

export function LocationPickerMap({
  latitude,
  longitude,
  onChange,
  height = 400,
}: LocationPickerMapProps) {
  const { data: dashboard, isLoading } = useQuery({
    queryKey: ['ops', 'dashboard'],
    queryFn: () => api.get<DashboardView>('/ops/dashboard'),
  });

  const config = dashboard?.map;

  const khungRef = useRef<HTMLDivElement>(null);
  const banDoRef = useRef<L.Map | null>(null);
  const lopMarkerRef = useRef<L.LayerGroup | null>(null);

  useEffect(() => {
    if (!khungRef.current || !config || banDoRef.current) {
      return;
    }
    const banDo = L.map(khungRef.current, {
      center: latitude && longitude ? [latitude, longitude] : [config.centerLat, config.centerLng],
      zoom: latitude && longitude ? 14 : config.defaultZoom,
      attributionControl: true,
    });

    L.tileLayer(config.tileUrl, {
      maxZoom: config.maxZoom,
      attribution: config.attribution,
    }).addTo(banDo);

    lopMarkerRef.current = L.layerGroup().addTo(banDo);
    banDoRef.current = banDo;

    banDo.on('click', (e: L.LeafletMouseEvent) => {
      const { lat, lng } = e.latlng;
      if (onChange) {
        onChange(lat, lng);
      }
    });

    const theoDoi = new ResizeObserver(() => banDo.invalidateSize());
    theoDoi.observe(khungRef.current);

    return () => {
      theoDoi.disconnect();
      banDo.remove();
      banDoRef.current = null;
      lopMarkerRef.current = null;
    };
  }, [config, latitude, longitude, onChange]);

  useEffect(() => {
    const lop = lopMarkerRef.current;
    if (!lop) {
      return;
    }
    lop.clearLayers();

    if (latitude && longitude) {
      // Use default leaflet marker style for picker but avoid icon issues by using divIcon
      const icon = L.divIcon({
        className: '',
        iconSize: [16, 16],
        iconAnchor: [8, 8],
        html: `<span style="display:block;width:16px;height:16px;border-radius:50%;background:#165bb6;border:2px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.35)"></span>`,
      });
      L.marker([latitude, longitude], { icon }).addTo(lop);
    }
  }, [latitude, longitude]);

  if (isLoading) {
    return <Spin />;
  }

  if (!config) {
    return <Empty description="Chưa tải được cấu hình bản đồ" />;
  }

  return (
    <div
      ref={khungRef}
      style={{ width: '100%', height, borderRadius: 6, overflow: 'hidden', cursor: 'crosshair' }}
    />
  );
}
