import { create } from "zustand";
import { persist } from "zustand/middleware";
import { addFavoriteApi, fetchFavoritesApi, fetchUserMeApi, removeFavoriteApi } from "../api/user";

export interface User {
  id: number;
  username: string;
  nickname: string;
  role: "user" | "admin";
  avatar: string;
  createTime: string;
}

interface AppState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  favorites: number[];

  setSession: (payload: { user: User; token: string }) => void;
  logout: () => void;
  refreshUser: () => Promise<void>;
  loadFavorites: () => Promise<void>;
  toggleFavorite: (modelId: number) => Promise<void>;
  isFavorite: (modelId: number) => boolean;
}

export const useStore = create<AppState>()(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      favorites: [],

      setSession: ({ user, token }) => {
        set({
          user,
          token,
          isAuthenticated: true,
        });
      },

      logout: () => {
        set({
          user: null,
          token: null,
          isAuthenticated: false,
          favorites: [],
        });
      },

      refreshUser: async () => {
        try {
          const res = await fetchUserMeApi();
          if (res.code === 200 && res.data) {
            const u = res.data;
            set({
              user: {
                id: u.id,
                username: u.username,
                nickname: u.nickname,
                role: u.role,
                avatar: u.avatar || "🧑",
                createTime: u.createTime || "",
              },
              isAuthenticated: true,
            });
          }
        } catch {
                    // 令牌过期或无效 —— 强制退出登录
          get().logout();
        }
      },

      loadFavorites: async () => {
        try {
          const res = await fetchFavoritesApi();
          if (res.code === 200 && res.data) {
            set({ favorites: res.data });
          }
        } catch {
                    // 未登录或服务端异常 —— 忽略
        }
      },

      toggleFavorite: async (modelId) => {
        const current = get().favorites;
        const isFav = current.includes(modelId);
        try {
          if (isFav) {
            await removeFavoriteApi(modelId);
            set({ favorites: current.filter((id) => id !== modelId) });
          } else {
            await addFavoriteApi(modelId);
            set({ favorites: [...current, modelId] });
          }
        } catch {
                    // 失败时本应回滚，但需要重新拉取数据，暂不处理
        }
      },

      isFavorite: (modelId) => get().favorites.includes(modelId),
    }),
    {
      name: "ai-modelhub",
      partialize: (state) => ({
        token: state.token,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
);
