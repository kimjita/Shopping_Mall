import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { Me, SkinConcern, SkinTone, SkinType } from './types'

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; phone: string }) =>
      apiFetch<Me>('/api/members/me', {
        method: 'PATCH',
        auth: 'required',
        json: body,
      }),
    onSuccess: (me) => queryClient.setQueryData(['me'], me),
  })
}

export function useUpdateSkinProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { skinType: SkinType | null; skinTone: SkinTone | null; skinConcerns: SkinConcern[] }) =>
      apiFetch<Me>('/api/members/me/skin-profile', {
        method: 'PUT',
        auth: 'required',
        json: body,
      }),
    onSuccess: (me) => queryClient.setQueryData(['me'], me),
  })
}

export const useChangePassword = () =>
  useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      apiFetch<void>('/api/members/me/password', {
        method: 'PATCH',
        auth: 'required',
        json: body,
      }),
  })

// 로그아웃·캐시 정리는 호출하는 화면이 한다.
export const useWithdraw = () =>
  useMutation({
    mutationFn: (password: string) =>
      apiFetch<void>('/api/members/me', {
        method: 'DELETE',
        auth: 'required',
        json: { password },
      }),
  })
