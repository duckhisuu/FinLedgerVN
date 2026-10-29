import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  CheckCircle2,
  Copy,
  Check,
  QrCode,
  ArrowRight,
  Loader2,
  ArrowLeft,
} from 'lucide-react';
import { PublicGroupShareResponse, publicShareApi } from '../api/client';
import { formatVnd } from '../utils/format';

export function PublicSharePage() {
  const { shareToken } = useParams<{ shareToken: string }>();

  const [group, setGroup] = useState<PublicGroupShareResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const fetchDetails = async (token: string) => {
    try {
      const data = await publicShareApi.getPublicGroup(token);
      setGroup(data);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Không tìm thấy thông tin nhóm chia tiền.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!shareToken) {
      setError('Đường dẫn chia sẻ không hợp lệ.');
      setLoading(false);
      return;
    }

    fetchDetails(shareToken);

    // Live updates when a member settles via bank transfer
    const eventSource = new EventSource(publicShareApi.getStreamUrl(shareToken));
    eventSource.addEventListener('DEBT_SETTLED', () => {
      fetchDetails(shareToken);
    });
    return () => {
      eventSource.close();
    };
  }, [shareToken]);

  const handleCopy = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#090c10] flex items-center justify-center">
        <Loader2 className="w-7 h-7 animate-spin text-[#22c55e]" />
      </div>
    );
  }

  if (error || !group) {
    return (
      <div className="min-h-screen bg-[#090c10] text-slate-100 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-[#141923] border border-white/10 rounded-3xl p-6 text-center space-y-4">
          <p className="text-sm text-rose-300">{error || 'Không tìm thấy nhóm.'}</p>
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#22c55e] text-slate-950 text-xs font-bold"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Về trang chủ FinLedger</span>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#090c10] text-slate-100 py-8 px-4">
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-semibold text-[#22c55e] uppercase tracking-wider">
              Bảng thanh toán nhóm
            </span>
            <h1 className="text-2xl font-extrabold text-white mt-0.5">
              {group.groupName}
            </h1>
          </div>
          <Link
            to="/"
            className="px-3.5 py-2 rounded-xl bg-[#141923] border border-white/10 text-xs font-semibold text-slate-200 hover:bg-white/[0.06]"
          >
            Mở ứng dụng
          </Link>
        </div>

        {/* VietQR Settlements */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {group.settlements.length === 0 ? (
            <div className="md:col-span-2 bg-[#141923] border border-white/[0.06] rounded-2xl p-10 text-center">
              <CheckCircle2 className="w-10 h-10 text-[#22c55e] mx-auto mb-2" />
              <p className="text-sm font-semibold text-white">
                Tất cả các khoản chi trong nhóm đã được cân bằng!
              </p>
            </div>
          ) : (
            group.settlements.map((s) => (
              <div
                key={s.id}
                className="bg-[#141923] border border-white/[0.06] rounded-2xl p-5 space-y-4 flex flex-col justify-between"
              >
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 text-sm font-bold text-white">
                      <span>{s.fromMemberName}</span>
                      <ArrowRight className="w-4 h-4 text-slate-500" />
                      <span className="text-[#22c55e]">{s.toMemberName}</span>
                    </div>
                    {s.status === 'COMPLETED' ? (
                      <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/15 text-[#22c55e] text-xs font-semibold">
                        Đã thanh toán
                      </span>
                    ) : (
                      <span className="px-2.5 py-0.5 rounded-full bg-amber-500/15 text-amber-300 text-xs font-semibold">
                        Chờ chuyển khoản
                      </span>
                    )}
                  </div>

                  <p className="text-2xl font-extrabold mono-num text-white">
                    {formatVnd(s.amount)}
                  </p>
                </div>

                {s.status !== 'COMPLETED' && s.vietQrUrl && (
                  <div className="space-y-3">
                    <div className="bg-white rounded-2xl p-3 flex justify-center">
                      <img
                        src={s.vietQrUrl}
                        alt={`VietQR ${s.toMemberName}`}
                        className="w-48 h-auto object-contain rounded-lg"
                      />
                    </div>
                    <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#0c1017] border border-white/[0.05] text-xs">
                      <div className="flex items-center gap-1.5 text-slate-300">
                        <QrCode className="w-4 h-4 text-[#22c55e]" />
                        <span>Nội dung CK:</span>
                        <strong className="mono-num text-white">
                          {s.settlementCode}
                        </strong>
                      </div>
                      <button
                        onClick={() => handleCopy(s.settlementCode)}
                        className="text-[#22c55e] hover:underline flex items-center gap-1 font-semibold"
                      >
                        {copiedCode === s.settlementCode ? (
                          <>
                            <Check className="w-3.5 h-3.5" />
                            <span>Đã chép</span>
                          </>
                        ) : (
                          <>
                            <Copy className="w-3.5 h-3.5" />
                            <span>Sao chép</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
