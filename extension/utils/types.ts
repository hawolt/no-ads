export interface LiveSource {
  live: boolean;
  playlist: string | null;
}

export interface LocationChangeDetail {
  type: string;
  url: string;
}
